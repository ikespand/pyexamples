# -*- coding: utf-8 -*-
"""
Created on Sun Oct 24 11:12:24 2021

This class is a generalize class to load and parse data for UCI repository.
For now, this is tailor-made specifically for following dataset:
    https://archive.ics.uci.edu/ml/datasets/bike+sharing+dataset
For other dataset, user should not use `read_csv()` function.

@author: PC
"""

import os
from pathlib import Path
import requests, zipfile, io
import pandas as pd

class DataParserFromUci():
    """Class to download data in a zip file from the UCI repository but it should
    work with other URL as well. For now, there is not sanity check if a data 
    already exist. We simply redownload and replace it.
    """
    def __init__(self, url, data_dir = os.getcwd(), file_name = None):
        self.url = url
        self.data_dir = data_dir
        self.file_name = file_name        
        self.data_pipeline()
        
    def __create_data_dir(self):
        self.data_path = os.path.join(self.data_dir, os.path.basename(self.url).split(".")[0])
        Path(self.data_path).mkdir(parents=True, exist_ok=True)
        
    def data_pipeline(self):
        """
        A data pipeline which execute class functions in a pipeline manner.

        Returns
        -------
        None.

        """
        self.__create_data_dir()
        self.download_zip()
        if self.file_name is not None:
            self.read_csv()
        else:
            print("Use `read_csv(file_name)` class function read a file")
    
    def download_zip(self):
        """
        Download the zip file and extract it given an URL of the file.

        Returns
        -------
        Path
            Path of the data.

        """
        r = requests.get(self.url)
        z = zipfile.ZipFile(io.BytesIO(r.content))
        z.extractall(self.data_path)
        return self.data_path

    def print_readme(self):
        """
        If there is `Readme.txt` file and reading it for data description.

        Returns
        -------
        None.

        """
        readme_filename = os.path.join(self.data_path, "Readme.txt")
        if os.path.isfile(readme_filename):
            with open(readme_filename, "r", encoding="utf-8") as file:
                for line in file:
                    print(line.strip())
        else:
            print("[Warning] No `Readme.txt` file is available!")
            
    def read_csv(self, file_name):
        """
        Read a given file in the `data_path` using pandas.

        Parameters
        ----------
        file_name : str
            Name of the data file.

        Returns
        -------
        pd.DataFrame
            Loaded data in a dataframe.

        """
        data_filename = os.path.join(self.data_path, file_name)
        if os.path.isfile(data_filename):
            self.df = pd.read_csv(data_filename)
            self.sanitize_df()
            return self.df
        else:
            print("[Error] Given file `{}` doesn't exist.".format(data_filename))
    
    def sanitize_df(self):
        """
        This function drops unnecessary columns, convert data types and add an
        additional column for the timestamp.

        Returns
        -------
        None.

        """
        self.df["dteday"] = pd.to_datetime(self.df["dteday"])
        self.df["season"] = self.df["season"].astype("category")
        self.df["weekday"] = self.df["weekday"].astype("category")
        self.df["mnth"] = self.df["mnth"].astype("category")  
        self.df['timestamp'] = self.df["dteday"] +  pd.to_timedelta(self.df.hr, unit="h")
        # Let's focus on total number of users
        self.df.drop(["casual", "registered", "instant"], axis=1, inplace=True)
        
if __name__ == "__main__":
    url = r"https://archive.ics.uci.edu/ml/machine-learning-databases/00275/Bike-Sharing-Dataset.zip"
    data_parser_instance = DataParserFromUci(url)
    data_parser_instance.print_readme()
    hours_df = data_parser_instance.read_csv("hour.csv")
