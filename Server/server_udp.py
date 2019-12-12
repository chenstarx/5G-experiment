import time
import socket
import datetime
import threading
from pymongo import MongoClient

client = MongoClient()

host = "0.0.0.0"
port = 55802

class dbSaver (threading.Thread):
    def __init__(self, time):
        threading.Thread.__init__(self)
        self.time = time
        
    def save(self, recvData):
        try:
            rawData = recvData.split("\r\n")

            for dataItem in rawData:
                data = dataItem.split("; ")
                dbData = {}

                for item in data:
                    if item != "":
                        items = item.split(": ")
                        if (len(items) == 2 and items[1] != ""):
                            if (items[0] == "Time"):
                                dbData['Time'] = items[1]
                            if (items[0] == "CSQ"):
                                dbData['CSQ'] = items[1]
                            if (items[0] == "Index"):
                                dbData['Index'] = items[1]
                            if (items[0] == "Height"):
                                dbData['Height'] = items[1]
                            if (items[0] == "Lati"):
                                dbData['Latitude'] = items[1]
                            if (items[0] == "Long"):
                                dbData['Longitude'] = items[1]

                if ("Index" in dbData):
                    mongo = client.droneudp
                    table = mongo[self.time]
                    table.insert_one(dbData)
            
        except Exception as err:
            print(err)

udps = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)

udps.bind((host,port))

lastAddr = ''

print('Start listening on port', port)

while True:
    data, addr = udps.recvfrom(1024)

    if addr != lastAddr:
        dt = datetime.datetime.now()
        currentTime = dt.strftime('%m-%d %H:%M')

        saver = dbSaver(currentTime)

    lastAddr = addr

    saver.save(data.decode('utf-8'))

udps.close()