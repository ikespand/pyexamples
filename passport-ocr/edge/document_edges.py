"""Learned page corners and perspective correction for the local web scanner."""
import cv2
import numpy as np
from .mrz_specialist import order

def rectify_document(image,q):
    if q is None:return None,None
    q=order(np.asarray(q,dtype=np.float32))
    width=int(round(max(np.linalg.norm(q[1]-q[0]),np.linalg.norm(q[2]-q[3]))))
    height=int(round(max(np.linalg.norm(q[3]-q[0]),np.linalg.norm(q[2]-q[1]))))
    if min(width,height)<2:return None,None
    target=np.float32([[0,0],[width-1,0],[width-1,height-1],[0,height-1]])
    transform=cv2.getPerspectiveTransform(q,target)
    return cv2.warpPerspective(image,transform,(width,height),flags=cv2.INTER_LINEAR,borderMode=cv2.BORDER_REPLICATE),transform

class LearnedDocumentEdges:
    """DocAligner heatmap corners, exposed for original-coordinate cropping.

    Inference/postprocessing adapted from DocsaidLab DocAligner, Apache-2.0;
    see AGENTS.md for the source attribution. Detection is not document
    verification and is not guaranteed to contain the MRZ.
    """
    def __init__(self, model):
        self.model = model
    def detect(self,image):
        h,w=image.shape[:2]
        tensor=cv2.resize(image,(256,256)).transpose(2,0,1)[None].astype(np.float32)/255
        outputs=dict(zip([o.name for o in self.model.get_outputs()],self.model.run(None,{self.model.get_inputs()[0].name:tensor})))
        points=[]
        for pred in outputs['heatmap'][0][:4]:
            pred=cv2.resize(pred,(w,h));pred[pred<.3]=0
            _,mask=cv2.threshold(np.uint8(pred*255),0,255,cv2.THRESH_BINARY+cv2.THRESH_OTSU)
            contours,_=cv2.findContours(mask,cv2.RETR_EXTERNAL,cv2.CHAIN_APPROX_SIMPLE)
            if not contours:return None
            moment=cv2.moments(max(contours,key=cv2.contourArea))
            if not moment['m00']:return None
            points.append([moment['m10']/moment['m00'],moment['m01']/moment['m00']])
        q=order(np.float32(points));area=cv2.contourArea(q)
        return q if area>=.05*w*h and cv2.isContourConvex(q) else None
