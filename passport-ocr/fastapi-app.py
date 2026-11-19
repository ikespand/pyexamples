# -*- coding: utf-8 -*-
"""
Created on Sun Jun 12 17:23:53 2022

FastAPI based REST interface for passport data extraction.

@author: ikespand
"""

import uvicorn
from fastapi import FastAPI, File, UploadFile
from passport_eye_mrz import read_from_passport_v0
from PIL import Image
from io import BytesIO
import os
import uuid
import time

app = FastAPI()

# Super dirty way to keep apiKeys here
apiKeys = ["SP0123456", "PS0123456"]

v0 = FastAPI(version="0.0.0",
             title="Passport reader",
             description="Reads the MRZ from passport")

def read_imagefile(file) -> Image.Image:
    image = Image.open(BytesIO(file))
    filename = os.path.join("requested_image", str(uuid.uuid4())+".jpg" )
    image.save(filename)
    return image, filename

@app.get("/")
async def root():
    return {"message": "Welcome to API offerings."}

        
@v0.post("/mrz")
async def read_mrz(apiKey:str, file: UploadFile = File(...)):
    if apiKey in apiKeys:
        extension = file.filename.split(".")[-1] in ("jpg", "jpeg", "png")
        if not extension:
            return {"Error" : "Image must be jpg or png format!"}
        st = time.time()
        image, fname = read_imagefile(await file.read())
        ps_info = read_from_passport_v0(fname)
        tt = time.time() - st
        return {"data": ps_info, "time_taken_sec" : tt}
    else:
        return {"Error" : "Invalid apiKey!"}

app.mount("/api/v0", v0)

if __name__ == "__main__":
    uvicorn.run(app)