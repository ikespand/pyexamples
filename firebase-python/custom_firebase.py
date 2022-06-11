#!/usr/bin/python
# coding=utf-8
"""
Created on Sat May 14 17:11:05 2022

Related to: 
    https://ikespand.github.io/posts/firebase/

@author: ikespand@GitHub
"""

import pyrebase
import psutil
import platform
import time
import shutil
import datetime

def additional_sys_info():
    """
    Get the desired system metrics. (NOT USED HERE)

    Returns
    -------
    ainfo : dict
        DESCRIPTION.

    """
    ainfo = {}
    ainfo["machine"] = platform.machine()
    ainfo["ops"] = platform.system()
    ainfo["processor"] = platform.processor()
    ainfo["uname"] = platform.uname()
    ainfo["version"] = platform.version()
    return ainfo 
 
def get_device_name():
    """
    Get the device name which can be use as user or device id. I supposed that
    it will be unique for each device.

    Returns
    -------
    dn : str
        DESCRIPTION.

    """
    dn = platform.node()
    #dn = ("").join([i.replace('-','') for i in dn])
    return dn
    
def sys_info():
    """
    A dictionary which contains some additional metrics. 

    Returns
    -------
    info : TYPE
        DESCRIPTION.

    """
    disc_usage = shutil.disk_usage("/")                  
    info = {}
    info["ram_usage"] = psutil.virtual_memory()[2]
    info["cpu_usage"] = psutil.cpu_percent()
    info["disk_usage"] = disc_usage[1]/disc_usage[0]*100
    # Firebase doesn't support list and during the addition will convert to key-value pair
    info["sample_list"] = [1, 2, 3, 4, 5, 6]
    return info 


class SystemDataFirebaseRt():
    """
    A class to wrap the functionality of pyrebase for specific purpose of sending
    data to the Firebase' realtime database. 
    """
    def __init__(self, cred_txt_fname):
        self.cred_txt_fname = cred_txt_fname
        self.fb = self.initialize_firebase()
        self.db = self.fb.database()
    
     
    def read_cred(self)->dict:
        """
        Read the credentials for the Firebase from the file.

        Returns
        -------
        dict
            DESCRIPTION.

        """
        d = {}
        with open(self.cred_txt_fname) as f:
            for line in f:
                (key, val) = line.split(': "')
                d[key] = val.split('"')[0]
        return d
    
    def initialize_firebase(self):
        """
        Initialize the firebase data with the given credentials. 
        TODO: Method to check if everything went well.

        Returns
        -------
        firebase : TYPE
            DESCRIPTION.

        """
        config = self.read_cred()
        firebase = pyrebase.initialize_app(config)
        #_auth = firebase.auth()
        return firebase

    @staticmethod
    def get_timestamp():
        """
        Get the formatted timestamp. Assuming that there is little to no time 
        lag in calling the data upsert method and this function.
        
        Returns
        -------
        str/list
            DESCRIPTION.
    
        """
        ts = time.time()
        return datetime.datetime.fromtimestamp(ts).strftime('%d%m%YT%H%M%S')
        #return datetime.datetime.fromtimestamp(ts).strftime('%d-%m-%Y-%H-%M-%S').split("-")
    
    def add_data_to_firebase(self, data):    
        """
         The schema is:
            SystemName > Timestamp > Data
        But, following can be better:
            SystemName > YY > MM > DD > HH > MM > SS > Data
            This could allow the ease in access?

        Parameters
        ----------
        data : TYPE
            DESCRIPTION.

        Returns
        -------
        resp : TYPE
            DESCRIPTION.

        """
        # data = sys_info()
        #resp = self.db.push(data)
        resp = self.db.child(get_device_name()).child(self.get_timestamp()).set(data)
        return resp
    
    def get_all_data(self):
        """
        Crude way to get all data from the FB.

        Returns
        -------
        None.

        """
        val = self.db.child().get()
        # print(val.val())
        return val.val()
# %%

if __name__ == "__main__":
    # Keep the credential in a `credential.txt` file within this folder.
    fb = SystemDataFirebaseRt(cred_txt_fname = r"credential.txt")
    ctr = 0
    res = []
    while ctr < 3:
        print("Instance: ", ctr)
        data = sys_info()
        res.append(fb.add_data_to_firebase(data))
        time.sleep(2)
        ctr+=1
    print("Done!")
    
    # Retrive the data from the list
    first_sample_list =  list(fb.get_all_data()["DESKTOP-M3PCI07"].items())[0][1]["sample_list"]
    _ = [print(l) for l in first_sample_list]


