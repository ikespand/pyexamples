#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Created on Fri Nov 19 18:55:32 2021

Objective of this script is get some analytics of YouTube's video and also 
download the thumbnail.

@author: ikespand
"""

from googleapiclient.discovery import build
import requests
import os
from pathlib import Path
import pandas as pd

# My YouTube API key is stored in environment variable, so getting them from 
# there. Alternatively, one can paste directly here.
api_key = os.environ["YOUTUBEAPIKEY"]

# %%

class YoutubeApi():
    def __init__(self, api_key):
        self.api_key = api_key
        self.youtube = build('youtube', 'v3', developerKey = self.api_key)

    def get_video_ids_for_channel(self, channel_id):
        
        contentdata = self.youtube.channels().list(id = channel_id,part='contentDetails').execute()
    
        playlist_id = contentdata['items'][0]['contentDetails']['relatedPlaylists']['uploads']
        videos = []
        next_page_token = None
        
        while 1:
            res = self.youtube.playlistItems().list(playlistId=playlist_id, 
                                            part='snippet', 
                                            maxResults=50,
                                            pageToken=next_page_token).execute()
            videos += res['items']
            next_page_token = res.get('nextPageToken')
        
            if next_page_token is None:
                break
        
        video_ids = list(map(lambda x:x['snippet']['resourceId']['videoId'], videos))
        
        searched_videos = videos
        video_ids = []
        publish_ts = []
        title = []
        thumbnail_def = []
        thumbnail_mq = []        
        thumbnail_hq = []
        channel_title = []
        for video in searched_videos:
           video_ids.append(video["snippet"]["resourceId"]["videoId"])
           publish_ts.append(video["snippet"]["publishedAt"])
           title.append(video["snippet"]["title"])
           thumbnail_def.append(video["snippet"]["thumbnails"]["default"]["url"])
           thumbnail_mq.append(video["snippet"]["thumbnails"]["medium"]["url"])
           thumbnail_hq.append(video["snippet"]["thumbnails"]["high"]["url"])
           channel_title.append(video["snippet"]["channelTitle"])         
    
        data={'video_id': video_ids,
              'publish_ts':publish_ts,
              'title':title,
              'thumbnail_def':thumbnail_def,
              'thumbnail_mq':thumbnail_mq,
              'thumbnail_hq':thumbnail_hq,
              'channel_title':channel_title}    
        
        return video_ids, videos, pd.DataFrame(data) 
    
    def get_video_stats(self, video_ids):
        """
        Get the statistics of a videos given in list form.

        Parameters
        ----------
        video_ids : list
            DESCRIPTION.

        Returns
        -------
        stats : TYPE
            DESCRIPTION.

        """
        stats = []
        for i in range(0, len(video_ids), 40):
            res = (self.youtube).videos().list(id=','.join(video_ids[i:i+40]),part='statistics').execute()
            stats += res['items']
        return stats


    def summerize_stats(self, channel_id):
        """
        Summerize the statistics of all videos in a given channel id.

        Parameters
        ----------
        channel_id : TYPE
            DESCRIPTION.

        Returns
        -------
        df : TYPE
            DESCRIPTION.

        """
        video_ids, videos, _ = self.get_video_ids_for_channel(channel_id)
        stats = self.get_video_stats(video_ids)
    
        title=[]
        liked=[]
        disliked=[]
        views=[]
        url=[]
        comment=[]
        for i in range(len(videos)):      
                title.append((videos[i])['snippet']['title'])
                url.append("https://www.youtube.com/watch?v="+(videos[i])['snippet']['resourceId']['videoId'])
                liked.append(int((stats[i])['statistics']['likeCount']))
                disliked.append(int((stats[i])['statistics']['dislikeCount']))
                views.append(int((stats[i])['statistics']['viewCount']))
                comment.append(int((stats[i])['statistics']['commentCount']))
                
        
        data={'video_id': video_ids,
              'title':title,
              'url':url,
              'liked':liked,
              'disliked':disliked,
              'views':views,
              'comment':comment}
        
        df=pd.DataFrame(data)
        return df

# %%

def get_channelid_from_video_url(api_key, video_url):
    """
    Returns the channel id for a given video url.

    Parameters
    ----------
    api_key : TYPE
        DESCRIPTION.
    video_url : TYPE
        DESCRIPTION.

    Returns
    -------
    TYPE
        DESCRIPTION.

    """
    video_id = video_url.split("v=")[1]
    url = r"https://youtube.googleapis.com/youtube/v3/videos?part=snippet&id={}&key={}".format(video_id, api_key)
    resp = requests.get(url)
    if resp.status_code == 200:
        return resp.json()["items"][0]["snippet"]["channelId"]
    else:
        print("Something went wrong! See message : ", resp.text)
        
def get_channelid_from_username(api_key, user_name):
    """
    Sometime doesn't work, therefore use: https://www.youtube.com/account_advanced
    to find your user id and channel id.
    
    Returns the FIRST channel's ID for a given user if user has a YT channel.
    TODO: Extend it to provide all channel's id.

    Parameters
    ----------
    api_key : TYPE
        DESCRIPTION.
    user_name : TYPE
        DESCRIPTION.

    Returns
    -------
    TYPE
        DESCRIPTION.

    """
    url = r"https://www.googleapis.com/youtube/v3/channels?key={}&forUsername={}&part=id".format(api_key, user_name)
    resp = requests.get(url)
    if resp.json()["items"][0]["kind"] == "youtube#channel":
        return resp.json()["items"][0]["id"]
    else:
        print("Not a YouTube channel")
        
        
def get_thumbnail(video_id, output_dir = os.getcwd()):
    """
    Download the thumbnail from a given video id.

    Parameters
    ----------
    video_id : TYPE
        DESCRIPTION.
    output_dir : TYPE, optional
        DESCRIPTION. The default is os.getcwd().

    Returns
    -------
    TYPE
        DESCRIPTION.

    """
    url = r"https://img.youtube.com/vi/{}/0.jpg".format(video_id)
    response = requests.get(url)
    Path(output_dir).mkdir(parents=True, exist_ok=True)    
    if response.status_code == 200:
        with open(os.path.join(output_dir, video_id+".jpg"), 'wb') as f:
            f.write(response.content)
    return os.path.join(output_dir, video_id+".jpg")


def search_video_with_keywords(keywords, api_key, max_results = 2):
    url = r"https://www.googleapis.com/youtube/v3/search?part=snippet&maxResults={}&q={}&type=video&key={}".format(max_results, keywords, api_key)
    resp = requests.get(url)
    if resp.status_code == 200:
        searched_videos = resp.json()["items"]
        video_ids = []
        publish_ts = []
        title = []
        thumbnail_def = []
        thumbnail_mq = []        
        thumbnail_hq = []
        channel_title = []
        for video in searched_videos:
           video_ids.append(video["id"]["videoId"])
           publish_ts.append(video["snippet"]["publishedAt"])
           title.append(video["snippet"]["title"])
           thumbnail_def.append(video["snippet"]["thumbnails"]["default"]["url"])
           thumbnail_mq.append(video["snippet"]["thumbnails"]["medium"]["url"])
           thumbnail_hq.append(video["snippet"]["thumbnails"]["high"]["url"])
           channel_title.append(video["snippet"]["channelTitle"])         
    
        data={'video_id': video_ids,
              'publish_ts':publish_ts,
              'title':title,
              'thumbnail_def':thumbnail_def,
              'thumbnail_mq':thumbnail_mq,
              'thumbnail_hq':thumbnail_hq,
              'channel_title':channel_title}
        return pd.DataFrame(data)    
    
    else:
        print("Something went wrong with error message: {}".format(resp.text))
        return None

def get_video_stats_from_video_id(api_key, video_id):
    url = r"https://www.googleapis.com/youtube/v3/videos?part=statistics&key={}&id={}".format(api_key, video_id)
    #print(url)
    resp = requests.get(url)
    if resp.status_code == 200:
        vid_stat = resp.json()["items"][0]["statistics"]
        vid_stat_df = pd.DataFrame(columns=list(vid_stat.keys()))
        vid_stat_df.loc[0] = list(vid_stat.values())
        vid_stat_df["video_id"] = video_id
        return vid_stat_df
    else:
        print("Something went wrong with error message: {}".format(resp.text))
        return None
    
#%%

if __name__ == "__main__":    
    # 1. Get channel id from a video url 
        # OR Get ur channel id from: https://www.youtube.com/account_advanced
    
    channel_id = get_channelid_from_video_url(api_key, r"https://www.youtube.com/watch?v=B8asvNNZuoA")
    
    # 2. Get video id for the entire channel
    yt = YoutubeApi(api_key)
    video_ids, _, searched_videos = yt.get_video_ids_for_channel(channel_id=channel_id)
    stats = yt.summerize_stats(channel_id)
    # OR combine 1 and 2 get video id for any searched video
    #searched_videos = search_video_with_keywords("machinelearning", api_key, max_results = 2)
    #video_ids = searched_videos["video_id"]
    
    # 3. Download the thumbnail and video stats
    video_stats = []
    for vid in video_ids:
        __df = get_video_stats_from_video_id(api_key, vid)
        __df["thumbnail_location"] = get_thumbnail(vid, output_dir = "./thumbnails")
        video_stats.append(__df)
            
    video_stats_df = pd.concat(video_stats)
    video_stats_df = pd.merge(searched_videos, 
                          video_stats_df, 
                          left_on='video_id', 
                          right_on='video_id')
    
    with open("video_stats_df.csv", "a") as f:
        video_stats_df.to_csv("video_stats_df.csv", index=False)
    
    
    