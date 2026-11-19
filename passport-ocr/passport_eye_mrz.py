# -*- coding: utf-8 -*-
"""
Created on Sun Jun 12 12:02:04 2022

Simple wrapper around the `passporteye` for passport data extraction, can be used
for KYC. As usual limitations are unclear picture and other OCR related error. But,
good enough for MVP.

For windows, if tesseract throws an error then follow this:
    https://stackoverflow.com/questions/51677283/tesseractnotfounderror-tesseract-is-not-installed-or-its-not-in-your-path
    
@author: ikespand
"""

import matplotlib.pyplot as plt
#from mrz.checker.td1 import TD1CodeChecker, get_country
from passporteye import read_mrz
import matplotlib.image as mpimg
#pytesseract.pytesseract.tesseract_cmd = r'C:\Program Files\Tesseract-OCR\tesseract.exe'
import pandas as pd
import os
import string as st
from dateutil import parser
import cv2
import easyocr
import numpy as np
from mrz.checker.td1 import TD1CodeChecker, get_country
from mrz.checker.td2 import TD2CodeChecker
from mrz.checker.td3 import TD3CodeChecker

# plt.ioff()

reader=easyocr.Reader(lang_list=['en'])  # Enable gpu if available


log_file = r"logfile.csv"
cname = ["Id", "FirstName", "LastName", "PassportNumber", "PassportImage", "MzrImage"]



def read_from_passport_v0(image_file:str)->dict:
    """
    Function that will localize MRZ from the image and will
    also log the requests. 

    Parameters
    ----------
    image_file : str
        Path of the image.

    Returns
    -------
    mrz_dict : dict
        DESCRIPTION.

    """
    roi_save_fname = r'mrz_roi/' + os.path.basename(image_file)
    mrz = read_mrz(image_file, save_roi=True)
    mrz_dict = mrz.to_dict()
    _ = log_data_to_csv([[os.path.basename(image_file).split(".")[0],
                        mrz_dict["names"],
                        mrz_dict["surname"],
                        mrz_dict["number"],
                        image_file,
                        roi_save_fname]]
                        )
    mpimg.imsave(roi_save_fname, mrz.aux['roi'], cmap='gray')
    return mrz_dict


    
def log_data_to_csv(data:list):
    df = pd.DataFrame(data, columns = cname)
    if os.path.isfile(log_file):
        df.to_csv(log_file, header=None, mode='a', index=False)
    else:
        df.to_csv(log_file, index=False)
    return None
    
    
def parse_date(string, iob=True):
    date = parser.parse(string, yearfirst=True).date()
    return date.strftime('%d/%m/%Y')

def clean(string):
    return ''.join(i for i in string if i.isalnum()).upper()

def get_country_name(country_code):
    if '1' in country_code:
      country_code.replace('1', 'I')

    return country_code

def get_gender(code):
    if code in ['M', 'm', 'F', 'f']:
        sex = code.upper()
    elif code == '0':
        sex = 'M'
    else:
        sex = 'F'
    return sex

def read_from_passport_v1(image_file):
    user_info = {}
    roi_save_fname = r'mrz_roi/' + os.path.basename(image_file)
    mrz = read_mrz(image_file, save_roi=True)   
    mpimg.imsave(roi_save_fname, mrz.aux['roi'], cmap='gray')
    
    #image manipulation
    img = cv2.imread(roi_save_fname)
    img = cv2.resize(img, (1110, 140))
    img = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY) #convert image to grayscale to enchment the ocr process
    
    #remove < from mrz code
    allowlist = st.ascii_letters+st.digits+'< '
    code = reader.readtext(img, paragraph=False, detail=0, allowlist=allowlist)
    
    
    a, b = code[0].upper(), code[1].upper()

    if len(a) < 44:
        a = a + '<'*(44 - len(a))
    if len(b) < 44:
            b = b + '<'*(44 - len(b))

    #Split data to first name and surename
    surname_names = a[5:44].split('<<', 1)
    if len(surname_names) < 2:
        surname_names += ['']
    surname, names = surname_names

    # mapping data
    user_info['name'] = names.replace('<', ' ').strip().upper()
    user_info['surname'] = surname.replace('<', ' ').strip().upper()
    user_info['gender'] = get_gender(clean(b[20]))
    user_info['date_of_birth'] = parse_date(b[13:19])
    user_info['nationality'] = get_country_name(clean(b[10:13]))
    user_info['passport_type'] = clean(a[0:2])
    user_info['passport_number']  = clean(b[0:9])
    user_info['issuing_country'] = get_country_name(clean(a[2:5]))
    user_info['expiration_date'] = parse_date(b[21:27])
    user_info['personal_number'] = clean(b[28:42])
    
    return user_info



def read_from_passport_v2(image_file:str):
    """
    Localize MRZ from the document > 
    Save only MRZ RoI >
    

    Parameters
    ----------
    image_file : str
        DESCRIPTION.

    Returns
    -------
    user_info : TYPE
        DESCRIPTION.

    """
    mrz = read_mrz(image_file, save_roi=True)  
    # mrz_dict = mrz.to_dict()
    if mrz:
        user_info = {}
        # Save image for future training/analysis
        roi_save_fname = r'mrz_roi/' + os.path.basename(image_file)
        mpimg.imsave(roi_save_fname, mrz.aux['roi'], cmap='gray')
        
        #image manipulation
        img = cv2.cvtColor(np.array(mrz.aux['roi']*255).astype('uint8'), 
                           cv2.COLOR_GRAY2BGR)
        
        img = cv2.cvtColor( cv2.resize(img, (1110, 140)), cv2.COLOR_BGR2GRAY) #convert image to grayscale to enchment the ocr process
        
        #remove < from mrz code
        allowlist = st.ascii_letters+st.digits+'< '
        code = reader.readtext(img, paragraph=False, detail=0, allowlist=allowlist)
        
        # Handle condition where embossed informations are also detected.
        # Typically row length > 15
        code = [s for s in code if len(s) >= 15]

            
        
        code_joined = "\n".join(code).upper().replace(" ", "")
        # Extract with MRZ
        # TODO: Adapt this part to handle different type of docs. E.g., visa, passport, id card etc.
        
        if len(code_joined) == 92:
            decoded_mrz = TD1CodeChecker(code_joined)
        elif len(code_joined) == 73:
            decoded_mrz = TD2CodeChecker(code_joined)            
        elif len(code_joined) == 89:
            decoded_mrz = TD3CodeChecker(code_joined)
        else:
            return None
        user_info['name'] = decoded_mrz.fields().name
        user_info['surname'] = decoded_mrz.fields().surname
        user_info['country_code'] = decoded_mrz.fields().country
        user_info["country_name"] = get_country(decoded_mrz.fields().country)
        user_info['nationality'] = decoded_mrz.fields().nationality
        user_info['birth_date'] = parse_date(decoded_mrz.fields().birth_date)
        user_info['expiry_date'] = parse_date(decoded_mrz.fields().expiry_date)
        user_info['sex']  = decoded_mrz.fields().sex
        user_info['document_type'] = decoded_mrz.fields().document_type
        user_info['document_number'] = decoded_mrz.fields().document_number
        user_info['optional_data'] = decoded_mrz.fields().optional_data
        user_info["doc_error"] = decoded_mrz.report.errors
        user_info["doc_warning"] = decoded_mrz.report.warnings
        return user_info

    else: 
        return None
# %%
if __name__ == "__main__":
    import glob
    sample_imgs = glob.glob("sample_img/*.jpeg")
    
    for img_file in sample_imgs:
        print(f"Processing {img_file}")
        mrz_dict = read_from_passport_v2(image_file=img_file)
        roi_img_file = r'mrz_roi/' + os.path.basename(img_file)
        
        img = mpimg.imread(img_file)
        img_roi = mpimg.imread(roi_img_file)
        
        plt.imshow(img)
        plt.axis('off') 
        plt.show()
        plt.imshow(img_roi)
        plt.axis('off') 
        plt.show()
        print(mrz_dict)
        print("-------------------------------------------------")
    
