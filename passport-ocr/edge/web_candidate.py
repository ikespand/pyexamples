"""Local page-first web adapter for the pinned MRZ candidate cascade.

All inputs are decoded locally. This adapter does not correct characters or
search substitutions to satisfy MRZ checksums.
"""
import time

import cv2
import numpy as np

from .decoder import decode
from .document_edges import LearnedDocumentEdges, rectify_document
from .mrz_candidate import CandidateScanner
from .mrz_specialist import CHARS as SPECIALIST_CHARS, decode_tokens, warp


SPOT_MEAN_GATE = 0.994124
NAME_MIN_GATE = 0.839322
_scanner = None
_page_detector = None


def _ensure_models():
    global _scanner, _page_detector
    if _scanner is None:
        _scanner = CandidateScanner()
        _page_detector = LearnedDocumentEdges(_scanner.aligner.model)
    return _scanner, _page_detector


def detect_page(image):
    scanner, detector = _ensure_models()
    started = time.perf_counter()
    corners = detector.detect(image)
    h, w = image.shape[:2]
    if corners is not None:
        left, top = np.min(corners, axis=0)
        right, bottom = np.max(corners, axis=0)
        if (right-left)*(bottom-top) < .10*w*h:
            corners = None
    elapsed = (time.perf_counter()-started)*1000
    return dict(
        pageFound=corners is not None,
        corners=corners.tolist() if corners is not None else [],
        imageWidth=w,
        imageHeight=h,
        detectionMs=elapsed,
        initializationMs=scanner.initialization_ms,
    )


def crop_page(image, corners):
    page, _ = rectify_document(image, np.asarray(corners, dtype=np.float32))
    if page is None:
        return None
    scale = min(1.0, 4096.0/max(page.shape[:2]))
    if scale < 1:
        page = cv2.resize(page, (round(page.shape[1]*scale), round(page.shape[0]*scale)), interpolation=cv2.INTER_AREA)
    return page


def _softmax(logits):
    shifted = logits - np.max(logits, axis=-1, keepdims=True)
    probabilities = np.exp(shifted)
    probabilities /= np.sum(probabilities, axis=-1, keepdims=True)
    return probabilities


def _spotter_read(scanner, image):
    h, w = image.shape[:2]
    pad = abs(h-w)//2
    padding = (pad,pad,0,0) if h<w else (0,0,pad,pad)
    padded = cv2.copyMakeBorder(image, *padding, cv2.BORDER_CONSTANT, value=(0,0,0))
    tensor = cv2.resize(padded,(512,512)).transpose(2,0,1)[None].astype(np.float32)/255
    logits = scanner.spotter.model.run(None,{scanner.spotter.model.get_inputs()[0].name:tensor})[0][0]
    probabilities = _softmax(logits)
    indices = np.argmax(probabilities,axis=-1)
    tokens, scores, line_scores = [], [], [[]]
    for timestep,index in enumerate(indices):
        if index == 1:
            break
        if index == 0:
            continue
        token = scanner.spotter.chars[index]
        tokens.append(token)
        if token == '&':
            line_scores.append([])
        else:
            score = float(probabilities[timestep,index])
            scores.append(score)
            line_scores[-1].append(score)
    lines = ''.join(tokens).split('&')
    return decode('\n'.join(lines)), line_scores, float(np.mean(scores)) if scores else 0.0


def _specialist_read(scanner, image):
    quad = scanner.specialist.detect(image)
    if quad is None:
        return dict(status='no_mrz',normalizedLines=[],fields={},checks={},warnings=[]), []
    crop = warp(image,quad)
    if crop is None:
        return dict(status='no_mrz',normalizedLines=[],fields={},checks={},warnings=[]), []
    tensor = cv2.resize(crop,(640,64)).transpose(2,0,1)[None].astype(np.float32)/255
    logits = scanner.specialist.models['rec'].run(None,{'input':tensor})[0][0]
    probabilities = _softmax(logits)
    indices = np.argmax(probabilities,axis=-1)
    line_scores=[[]]
    for timestep,index in enumerate(indices):
        if index == 1:
            break
        if index == 0:
            continue
        token=SPECIALIST_CHARS[index]
        if token == '<SEP>':
            line_scores.append([])
        elif token not in ('<PAD>','<EOS>'):
            line_scores[-1].append(float(probabilities[timestep,index]))
    rows=decode_tokens(logits)
    return decode('\n'.join(rows)),line_scores


def _name_minimum(result,line_scores):
    lines=result.get('normalizedLines',[])
    if len(lines)!=2 or any(len(line)!=44 for line in lines) or len(line_scores)!=2 or len(line_scores[0])<44:
        return 0.0
    return float(min(line_scores[0][5:44]))


def scan_rectified(image):
    scanner,_=_ensure_models()
    started=time.perf_counter()
    spotting_start=time.perf_counter()
    direct,direct_scores,spot_mean=_spotter_read(scanner,image)
    spotting_ms=(time.perf_counter()-spotting_start)*1000
    direct_td3=(direct.get('status')=='valid' and direct.get('format')=='TD3' and len(direct.get('normalizedLines',[]))==2)
    if direct_td3:
        result=direct
        selected_scores=direct_scores
        source='spotter'
        fallback_ms=0.0
    else:
        fallback_start=time.perf_counter()
        result,selected_scores=_specialist_read(scanner,image)
        fallback_ms=(time.perf_counter()-fallback_start)*1000
        source='specialist_fallback'
    name_min=_name_minimum(result,selected_scores)
    gate_passed=spot_mean>=SPOT_MEAN_GATE or name_min>=NAME_MIN_GATE
    lines=result.get('normalizedLines',[])
    valid=(result.get('status')=='valid' and result.get('format')=='TD3' and len(lines)==2 and all(len(line)==44 for line in lines))
    raw='\n'.join(lines)
    if not valid or not gate_passed:
        result=dict(result)
        result['status']='retake'
        result['fields']={}
        result['checks']={}
        result['normalizedLines']=[]
        raw=''
        result['warnings']=['This read is uncertain. Hold the passport landscape with the MRZ at the bottom, keep all four page edges visible, focus, and avoid glare.']
    result.update(
        rawText=raw,
        engine='aligned MRZ cascade',
        recognitionSource=source,
        confidenceGatePassed=bool(gate_passed),
        timingsMs=dict(spotting=spotting_ms,fallback=fallback_ms,total=(time.perf_counter()-started)*1000),
        imageWidth=int(image.shape[1]),
        imageHeight=int(image.shape[0]),
        validationLevel='development_confidence_gate',
    )
    return result


def run(image,detect_only=False):
    page=detect_page(image)
    if detect_only:
        return dict(page, candidates=[page['corners']] if page['pageFound'] else [])
    if not page['pageFound']:
        return dict(status='retake',fields={},checks={},warnings=['Could not find all four page edges. Use a plain contrasting surface and keep the whole passport visible.'],
                    pageFound=False,imageWidth=page['imageWidth'],imageHeight=page['imageHeight'],timingsMs=dict(pageDetection=page['detectionMs'],total=page['detectionMs']))
    crop_start=time.perf_counter()
    cropped=crop_page(image,page['corners'])
    if cropped is None:
        return dict(status='retake',fields={},checks={},warnings=['Could not straighten the page. Try again with all four edges visible.'],pageFound=False)
    crop_ms=(time.perf_counter()-crop_start)*1000
    result=scan_rectified(cropped)
    result.update(pageFound=True,documentAligned=True,pageDetectionMs=page['detectionMs'],pageCropMs=crop_ms,
                  imageWidth=page['imageWidth'],imageHeight=page['imageHeight'],pageWidth=int(cropped.shape[1]),pageHeight=int(cropped.shape[0]))
    return result
