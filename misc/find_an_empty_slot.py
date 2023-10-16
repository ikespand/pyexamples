#!/usr/bin/env python
# coding: utf-8
"""
Created on Tue Sep 19 17:26:18 2023

Find the available slots for tables for OktoberFest and notify me on WhastApp. You
should have logged in to your WhatsApp in your default browser. This script then try
to find an empty slot via website and notfies you or your predefined contacts.

This is for educational purpose! I personally am not aware about the legality of such tools.

@author: PC
"""
from logbook import Logger, StreamHandler
import sys
import pandas  as pd
from bs4 import BeautifulSoup
import time
import random
import cloudscraper
import pywhatkit

StreamHandler(sys.stdout).push_application()
log = Logger('OktoBus')

numbers=["+12345697890xav"] # List of WhatsApp numbers for notification.

def check_availability(query_date:str, query_tod:str):
    scraper = cloudscraper.create_scraper(browser={'browser': 'firefox','platform': 'windows','mobile': False})
    url = r"https://www.oktoberfest-booking.com/de/reseller-angebote"
    resp = scraper.get(url)
    #header = {"User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.5845.188 Safari/537.36",
    #          "X-Requested-With": "XMLHttpRequest"}
    #resp = requests.get(url, headers=header)
    if resp.status_code == 200:
        S = BeautifulSoup(resp.text, "lxml")
        poi = S.find_all("span", class_="tw-font-normal")
        _kaufen = S.find_all("button", class_= "md:tw-max-w-[192px]")
        _kaufen = [i.text.strip() for i in _kaufen]

        unwind=[]
        for date in poi:
            unwind.append(date.text)
            
        df = pd.DataFrame()
        df["date"]= unwind[0:-1:4]
        df["tod"]=unwind[1:-1:4]
        df["person"]=unwind[2:-1:4]
        #df["status"] = kaufen
        for id, row in df.iterrows():
            if query_date == row["date"]:
                #log.info(f"Found one on query date {row['date']} for time {row['tod']}")
                if query_tod == row["tod"]:
                    log.info(f"SUCCESS! Found for query date {row['date']} for TOD {row['tod']}")
                    return row
        log.warning("Couldn't find any exact matching date/time. Current DATAFRAME is >>")
        print(df)
        return False
    else:
        log.error("REQUEST FAILED. See the raw response!")
        log.error(resp.text)
        return None
    
def find_slot(query_date: str,query_tod:str, time_sleep = 300):
    result = check_availability(query_date=query_date, query_tod=query_tod)
    if type(result)!=bool: # Dirty logic
        log.info("Notifying on whatsapp")
        msg = f"[OktoBOT] I have found an empty slot on date *{result['date']}* for *{result['tod']}* for *{result['person']}*"
        send_whatsapp_message(numbers, msg)
        return True
    else:
        time_sleep = random.randint(421,556)
        log.info(f"Retrying in {time_sleep} seconds")
        time.sleep(time_sleep)
        find_slot(query_date, query_tod, time_sleep = time_sleep)

def send_whatsapp_message(numbers:list, msg: str):
    "Copied from Internet!"
    for num in numbers:
        try:
            pywhatkit.sendwhatmsg_instantly(
                phone_no=num,
                message=msg,
                wait_time=random.randint(7,15),
                tab_close=True
                
            )
        except Exception as e:
            print(str(e))
           
# %%
if __name__ == "__main__":
    my_date = "30.09.2023"
    my_time = "Abend"
    print(f"STARTING TO QUERY FOR {my_date} and {my_time}")
    success = find_slot(query_date=my_date, query_tod= my_time,time_sleep = 100)

