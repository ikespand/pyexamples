# -*- coding: utf-8 -*-
"""
Created on Tue Oct 26 11:59:34 2021

This contain some test to warrant the functionality of the module. Here, we basically
perform unit test and few regression test. 
Ideally, this should be structured with modulewise and should bein a test folder.

@author: PC
"""

from data_parser import DataParserFromUci
from deploy_model import DeployModel
#import pytest
import pandas as pd
import numpy as np

def test_data_parser():
    """Function to test data_parser module"""
    url = r"https://archive.ics.uci.edu/ml/machine-learning-databases/00275/Bike-Sharing-Dataset.zip"
    data_parser_instance = DataParserFromUci(url)
    hours_df = data_parser_instance.read_csv("hour.csv")
    assert isinstance(data_parser_instance, DataParserFromUci)
    assert isinstance(hours_df, pd.DataFrame)
    assert data_parser_instance.df['timestamp'].dtype == '<M8[ns]'

def test_deploy_model():
    """Function to test deployment script"""
    model_path = r"./26102021_BikeRentalPrediction.sav"
    prediction_instance = DeployModel(model_path)
    cnt_pdct = prediction_instance.make_prediction_from_csv("test_dataset/test_parameters.csv")
    assert isinstance(prediction_instance, DeployModel)
    # Prediction can never be zero
    assert cnt_pdct > 0