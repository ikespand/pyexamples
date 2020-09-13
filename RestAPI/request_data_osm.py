"""
ikespand@GitHub

A sample program to fetch the data from OSM (via Overpass) for a given 
coordinate as centre and a radius, to search for a toilet and its direct
distance (which may not be the exact distance depending actual streets).
Therefore, this example illustrates:
    1. How to use web api via request
    2. Postprocess the json data with pandas
    3. Create our own API with postprocess data with`main_api.py` 
"""

import geopandas as gpd
import pandas as pd
import math
import requests
import numpy as np 
from shapely.geometry import Point, LineString
from itertools import groupby
from settings import *  # THIS HOLDS API KEYS
from keplergl_cli.keplergl_cli import Visualize

#%% INPUTS
class FindToilets():
    def __init__(self,radius=None,lat=None,lon=None):
        if radius is None:
            self.radius = 1000
            print("No radius is provide, choosing as 100 m")
        else:
            self.radius = radius  # In meters
        if lat is None:
            self.lat = "48.689357"
            print("No coordinates? Choosing default '48.689357,9.000451'")
        else:
            self.lat = lat  # Should come from the request
        if lon is None:
            self.lon = "9.000451"
            print("No coordinates? Choosing default '48.689357,9.000451'")
        else:
            self.lon = lon  # Should come from the request
        #self.coord = self.lat, self.lon
        self.query = self.build_query()
        self.sever = 'http://overpass-api.de/api/interpreter'

    def provide_dataframe(self):
        osm_id = []
        lan_lon = []
        tags = []
        distance_from_src = []
        data = self.fetch_data_from_overpass()
        for i in range(0, len(data)):
            osm_id.append(data[i]['id'])
            lan_lon.append(Point(data[i]['lat'], data[i]['lon']))
            tags.append(data[i]['tags'])
            distance_from_src.append(FindToilets.calculate_distance(self.lat, 
                                                        self.lon,
                                                        data[i]['lat'], 
                                                        data[i]['lon']
                                                        ))
        df = pd.DataFrame({'osm_id':osm_id,
                           'tags':tags,
                           'distance_from_src':distance_from_src})
        gdf = gpd.GeoDataFrame(df, geometry=lan_lon)
        return gdf

        
    def build_query(self):
        query_part_1 ='[out:json][timeout:25];(node["amenity"="toilets"]'
        query_part_2 = '(around:'+str(self.radius)+','+str(self.lat)+','+ str(self.lon)+'); );'
        query_part_3 = 'out body;>;out skel qt;'
        return query_part_1+query_part_2+query_part_3

    def fetch_data_from_overpass(self):
        response = requests.get(self.sever, params={'data': self.query})
        if response.status_code == 200:
            #print('Success!')
            return response.json()['elements']
        elif response.status_code == 404:
            print('Error')  
        return [0]
    
    @staticmethod
    def calculate_distance(lat1, lon1, lat2, lon2):
        """ Using the haversine formula, which is good approximation even at
        small distances unlike the Shperical Law of Cosines. This method has
        ~0.3% error built in.
        """
        
        dLat = math.radians(float(lat2) - float(lat1))
        dLon = math.radians(float(lon2) - float(lon1))
        lat1 = math.radians(float(lat1))
        lat2 = math.radians(float(lat2))
    
        a = math.sin(dLat/2) * math.sin(dLat/2) + \
            math.cos(lat1) * math.cos(lat2) * math.sin(dLon/2) * \
            math.sin(dLon/2)
    
        c = 2 * math.atan2(math.sqrt(a), math.sqrt(1-a))
    
        d = 6371 * c
    
        return d
    
# %%
find_wc = FindToilets().provide_dataframe()