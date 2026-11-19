"""Specialist MRZ model loading, region detection and sequence decoding.

Adapted from DocsaidLab/MRZScanner 7f98880115441c8e42424eda1dc8f1abd4e39377
and Capybara 85a0d2638f895b39f82965b137320c8aa92c517b (Apache-2.0).
BGR / 255, bilinear resize, Otsu largest contour, reference min-box warp.
No network, dictionary correction, filler padding, or checksum-driven search.
"""
import hashlib
from pathlib import Path
import cv2,numpy as np,onnxruntime as ort

MODELS=Path(__file__).resolve().parent.parent/'.tools/docsaid-models'
HASHES={'det':'0786844728d828dc68c19b844aa87e2b29fdba3a587468f2863824d116ffc269','rec':'07cef987fc1852aac12f18e1269fbaf1e2644579ca5743061d6f9cec7bef77c0'}
CHARS=['<PAD>','<EOS>','<SEP>']+list('ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789<')


def order(q):
    s=q[np.argsort(q[:,0])];left=s[:2][np.argsort(s[:2,1])];right=s[2:][np.argsort(s[2:,1])]
    return np.float32([left[0],right[0],right[1],left[1]])


def warp(image,q):
    (_, _),(w,h),_=cv2.minAreaRect(np.float32(q));w,h=int(max(w,h)),int(min(w,h))
    if w<2 or h<2:return None
    dst=np.float32([[0,0],[w,0],[w,h],[0,h]])
    return cv2.warpPerspective(image,cv2.getPerspectiveTransform(order(q),dst),(w,h))


def decode_tokens(logits):
    if logits.ndim!=2 or logits.shape[1]!=len(CHARS):raise ValueError('Unexpected recognizer classes')
    indices=logits.argmax(-1);tokens=[]
    for index in indices:
        if index==1:break
        if index!=0:tokens.append(CHARS[index])
    # This is direct sequence decoding, NOT CTC. Repeated fillers must survive.
    return ''.join(tokens).split('<SEP>')


class SpecialistScanner:
    def __init__(self, num_threads=2):
        options=ort.SessionOptions();options.intra_op_num_threads=num_threads;options.log_severity_level=3
        self.models={}
        for name,digest in HASHES.items():
            path=MODELS/(name+'.onnx')
            if not path.exists() or hashlib.sha256(path.read_bytes()).hexdigest()!=digest:raise RuntimeError('Pinned specialist model missing or changed')
            self.models[name]=ort.InferenceSession(str(path),options,providers=['CPUExecutionProvider'])

    def detect(self,image):
        h,w=image.shape[:2]
        if h<w:pad=(w-h)//2;padding=(pad,pad,0,0)
        else:pad=(h-w)//2;padding=(0,0,pad,pad)
        padded=cv2.copyMakeBorder(image,*padding,cv2.BORDER_CONSTANT,value=(0,0,0))
        tensor=cv2.resize(padded,(256,256)).transpose(2,0,1)[None].astype(np.float32)/255
        prediction=self.models['det'].run(None,{'input':tensor})[0][0]
        mask=cv2.resize(np.uint8(prediction*255),(padded.shape[1],padded.shape[0]))
        _,mask=cv2.threshold(mask,0,255,cv2.THRESH_BINARY+cv2.THRESH_OTSU)
        contours,_=cv2.findContours(mask,cv2.RETR_EXTERNAL,cv2.CHAIN_APPROX_SIMPLE)
        contours=[c for c in contours if len(c)>1]
        if not contours:return None
        contour=max(contours,key=cv2.contourArea)
        if cv2.contourArea(contour)<=0:return None
        q=order(cv2.boxPoints(cv2.minAreaRect(contour)).round(4))
        return q-np.float32([padding[2],padding[0]])
