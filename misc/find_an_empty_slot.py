#!/usr/bin/env python
# coding: utf-8
"""
Created on Tue Sep 19 17:26:18 2023

Find the available slots for tables for OktoberFest and notify me on WhastApp. You
should have logged in to your WhatsApp in your default browser. This script then try
to find an empty slot via website and notfies you or your predefined contacts.

This is for educational purpose! I personally am not aware about the legality of such tools.

@author: ikespand
"""
from logbook import Logger, StreamHandler
import sys
import pandas  as pd
from bs4 import BeautifulSoup
import time
import random
import cloudscraper
import pywhatkit
import re

StreamHandler(sys.stdout).push_application()
log = Logger('OktoBus')

numbers=["+1234567890"] # List of WhatsApp numbers for notification.

url = r"https://www.oktoberfest-booking.com/de/reseller-angebote"

user_agents = [
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.3', 
    'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_5) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/12.1.1 Safari/605.1.15',
    'Mozilla/5.0 (Windows NT 6.1; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/70.0.3538.77 Safari/537.36',
]

def get_random_user_agent():
    return random.choice(user_agents)

# Function to extract relevant information
def extract_info(entry):
    lines = entry.split("\n")
    lines = [line.strip() for line in lines if line.strip()]
    
    zelt = ""
    date_full = ""
    tod = ""
    date = ""
    time = ""
    totalperson = ""
    totalcost = ""
    
    for i, line in enumerate(lines):
        if "Festzelt" in line:
            zelt = line
        elif re.match(r'\w+,\s\d{2}\.\d{2}\.\d{4}', line):
            date_full = line
            # Extract date without the day of the week
            date = re.search(r'\d{2}\.\d{2}\.\d{4}', date_full).group(0)
        elif line in ["Vormittag", "Mittag", "Nachmittag", "Abend"]:
            tod = line
        elif re.match(r'\d{2}:\d{2}-\d{2}:\d{2}', line):
            time = line + " Uhr"
        elif "Personen" in line:
            totalperson = line
        elif "Summe" in line:
            totalcost = lines[i + 1].replace("€\xa0", "€ ").strip()

    return zelt, date, tod, time, totalperson, totalcost

    
def check_availability(query_date:str, query_tod:str):
    """Function to check availability for a date and ToD"""
    scraper = cloudscraper.create_scraper(browser={'browser': 'firefox','platform': 'windows','mobile': False})
    scraper.headers.update({'User-Agent': get_random_user_agent()})
    resp = scraper.get(url)
    #header = {"User-Agent": "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.5845.188 Safari/537.36",
    #          "X-Requested-With": "XMLHttpRequest"}
    #resp = requests.get(url, headers=header)
    if resp.status_code == 200:
        S = BeautifulSoup(resp.text, "lxml")
   

        poi = S.find_all("div", class_="tw-w-full")
        #_kaufen = S.find_all("button", class_= "md:tw-max-w-[192px]")
        #_kaufen = [i.text.strip() for i in _kaufen]

        unwind=[]
        for date in poi:
            unwind.append(date.text)
        
        filtered_list = list(filter(None, unwind))
        # TODO: Structure can change then following might not work!
        filtered_list1 = filtered_list[::8]
        extracted_data = [extract_info(entry) for entry in filtered_list1]        
        df = pd.DataFrame(extracted_data, columns=["zelt", "date", "tod", "time", "person", "totalcost"])
        # print(df)
       
        for id, row in df.iterrows():
            if query_date is None:
                if query_tod == row["tod"]:
                    log.info(f"SUCCESS! Found for query date {row['date']} for TOD {row['tod']}")
                    return row
            elif query_date == row["date"]:
                #log.info(f"Found one on query date {row['date']} for time {row['tod']}")
                if query_tod == row["tod"]:
                    log.info(f"SUCCESS! Found for query date {row['date']} for TOD {row['tod']}")
                    return row
        log.warning("Couldn't find any exact matching date/time. Current DATAFRAME is >>")
        print(df.to_string())
        return False
    else:
        log.error("REQUEST FAILED. See the raw response!")
        log.error(resp.text)
        return None
    
def find_slot(query_date: str, query_tod:str):
    result = check_availability(query_date=query_date, query_tod=query_tod)
    if type(result)!=bool: # Dirty logic
        log.info("Notifying on whatsapp")
        msg = f""" [OktoBOT] I have found an empty slot on in *{result['zelt']}* 
        date *{result['date']}* for *{result['tod']}* -- *{result['time']}* 
        for *{result['person']}*. It will cost *{result['totalcost']}*.
        Book now by visiting {url}""
        """
        send_whatsapp_message(numbers, msg)
        return True
    else:
        time_sleep = random.randint(555,999)
        log.info(f"Retrying in {time_sleep} seconds")
        time.sleep(time_sleep)
        find_slot(query_date, query_tod)

def send_whatsapp_message(numbers:list, msg: str):
    "Copied from Internet!"
    for num in numbers:
        try:
            pywhatkit.sendwhatmsg_instantly(
                phone_no=num,
                message=msg,
                wait_time=random.randint(25,35),
                tab_close=True
                
            )
        except Exception as e:
            print(str(e))
           
# %%
if __name__ == "__main__":
    my_date = None
    my_time = "Abend"
    print(f"STARTING TO QUERY FOR {my_date} and {my_time}")
    success = find_slot(query_date=my_date, query_tod= my_time)

