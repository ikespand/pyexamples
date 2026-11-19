import asyncio
import io
import json
from pathlib import Path

import cv2
import numpy as np
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import FileResponse, JSONResponse, Response

from .presentation import display_fields
from .web_candidate import crop_page, detect_page, run, scan_rectified

app = FastAPI(docs_url=None, redoc_url=None)
lock = asyncio.Lock()
MAX_UPLOAD_BYTES = 32_000_000
MAX_IMAGE_PIXELS = 16_000_000


@app.get('/')
def index():
    return FileResponse(Path(__file__).parent/'static/index.html', headers={'Cache-Control':'no-store'})


def validate_local_request(request):
    if request.headers.get('host') not in ('127.0.0.1:8000','localhost:8000'):
        raise HTTPException(403)
    if request.headers.get('origin') not in (None,'http://127.0.0.1:8000','http://localhost:8000'):
        raise HTTPException(403)


async def read_local_image(request):
    data = bytearray()
    async for chunk in request.stream():
        data.extend(chunk)
        if len(data)>MAX_UPLOAD_BYTES:
            raise HTTPException(413,'Frame too large')
    try:
        from PIL import Image, ImageOps
        with Image.open(io.BytesIO(data)) as im:
            if im.width*im.height > MAX_IMAGE_PIXELS:
                raise ValueError('Too many pixels')
            return cv2.cvtColor(np.asarray(ImageOps.exif_transpose(im).convert('RGB')),cv2.COLOR_RGB2BGR)
    except Exception:
        raise HTTPException(400,'Use an image up to 16 megapixels')


@app.post('/page/detect')
async def page_detect(request: Request):
    validate_local_request(request)
    if lock.locked():
        raise HTTPException(429,'Scanner busy')
    async with lock:
        image=await read_local_image(request)
        try:
            result=await asyncio.to_thread(detect_page,image)
            return JSONResponse(result,headers={'Cache-Control':'no-store'})
        except RuntimeError as exc:
            raise HTTPException(422,str(exc))


@app.post('/page/crop')
async def page_crop(request: Request):
    validate_local_request(request)
    if lock.locked():
        raise HTTPException(429,'Scanner busy')
    async with lock:
        image=await read_local_image(request)
        try:
            found=await asyncio.to_thread(detect_page,image)
            if not found['pageFound']:
                raise HTTPException(422,'Could not find all four page edges. Use a plain contrasting surface and keep the whole passport visible.')
            started=asyncio.get_running_loop().time()
            page=await asyncio.to_thread(crop_page,image,found['corners'])
            if page is None:
                raise HTTPException(422,'Could not straighten the page. Keep all four edges in view and try again.')
            elapsed=(asyncio.get_running_loop().time()-started)*1000
            ok,encoded=cv2.imencode('.jpg',page,[cv2.IMWRITE_JPEG_QUALITY,95])
            if not ok:
                raise HTTPException(500,'Could not prepare the page preview')
            info=dict(pageWidth=int(page.shape[1]),pageHeight=int(page.shape[0]),
                      imageWidth=found['imageWidth'],imageHeight=found['imageHeight'],
                      pageDetectionMs=found['detectionMs'],pageCropMs=elapsed,
                      initializationMs=found['initializationMs'])
            return Response(encoded.tobytes(),media_type='image/jpeg',headers={
                'Cache-Control':'no-store','X-Page-Info':json.dumps(info,separators=(',',':')),
                'X-Content-Type-Options':'nosniff'})
        except RuntimeError as exc:
            raise HTTPException(422,str(exc))


@app.post('/scan')
async def scan(request: Request, detect_only: bool = False, rectified: bool = False):
    validate_local_request(request)
    if lock.locked():
        raise HTTPException(429,'Scanner busy')
    async with lock:
        image=await read_local_image(request)
        try:
            if rectified and not detect_only:
                result=await asyncio.to_thread(scan_rectified,image)
            else:
                result=await asyncio.to_thread(run,image,detect_only)
            result['displayFields']=display_fields(result.get('fields') or {}) if result.get('fields') else []
            return JSONResponse(result,headers={'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'})
        except RuntimeError as exc:
            raise HTTPException(422,str(exc))
