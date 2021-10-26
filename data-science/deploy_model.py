# -*- coding: utf-8 -*-
"""
Created on Tue Oct 26 11:26:10 2021

Scirpt to deploy black-box model.

@author: PC
"""

import joblib
import pandas as pd

class DeployModel():
    """ A sample class which can be ditributred for model deployment in prodction.
    This can be used for batch job or for creating a REST API.
    TODO: Complete function `make_prediction_from_raw_variables()`
    """
    def __init__(self, model_path):
        self.model_path = model_path
        self.load_model()
        
    def load_model(self):
        """
        Load the model.

        Returns
        -------
        None.

        """
        self.rf = joblib.load(self.model_path)
    
    def make_prediction_from_csv(self, parameter_csv_path):
        """
        Returns the prediction by model.

        Parameters
        ----------
        parameter_csv_path : str
            Path of csv file which has processed features.

        Returns
        -------
        int
            Prediction of bike demand.

        """
        feature_df = pd.read_csv(parameter_csv_path)
        return self.rf.predict(feature_df)

    def make_prediction_from_raw_variables(self, raw_parameters):
        """
        This function returns the prediction by processing raw parameters i.e.
        without any pre-processing. This function handles all those steps. 

        Parameters
        ----------
        raw_parameters : pd.DataFrame
            raw parameters.

        Returns
        -------
        None.

        """
        pass
    
if __name__ == "__main__":
    model_path = r"./26102021_BikeRentalPrediction.sav"
    prediction_instance = DeployModel(model_path)
    cnt_pdct = prediction_instance.make_prediction_from_csv("test_dataset/test_parameters.csv")
    print("Future demand {}".format(cnt_pdct))