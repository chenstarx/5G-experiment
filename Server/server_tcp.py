import time
import socket
import datetime
import threading
from collections import deque
from pymongo import MongoClient

client = MongoClient()
production_env = True
host = "0.0.0.0"
port_recv = 55800
port_send = 55801

Latitude = ""
Longitude = ""
Temperature = ""
Height = ""
dataTemp = ""
dataFlag = False

isModule = False


class SendBackThread(threading.Thread):
    def __init__(self, socket_inst):
        threading.Thread.__init__(self)
        self.mySocket = socket_inst
        self.q = deque()
        self.mySocket.settimeout(120)

    def try_send_msg(self):
        msg_send = self.q.popleft()
        self.mySocket.sendall(bytes(msg_send, 'utf-8'))
        print("send: ", msg_send)
        # socket.write("") -> self.mySocket.sendall(bytes(message, 'utf-8'))

    def run(self):
        while True:
            if len(self.q) > 0:
                print(len(self.q))
                try:
                    self.try_send_msg()
                except:
                    pass

    def add(self, index_send, server_time_start, time_initial):
        server_duration = time.time() - server_time_start
        msg_send = "index: " + str(index_send) + "; server_process_time: " + \
                   str(int(server_duration * 1000)) + "; client_time_start: " + str(time_initial) + "\n"
        # self.q.append(msg_send)
        self.mySocket.sendall(bytes(msg_send, 'utf-8'))
        print("send: ", msg_send)




class myThread(threading.Thread):
    def __init__(self, socketInstance, time):
        threading.Thread.__init__(self)
        self.time = time
        self.mySocket = socketInstance
        self.iostream = self.mySocket.makefile('r')
        self.mySocket.settimeout(120)

    def run(self):
        while True:
            global Latitude, newSend
            global Longitude
            global Temperature
            global Height
            global dataTemp
            global dataFlag

            try:
                # rawData = self.mySocket.recv(1024).decode()
                rawData = self.iostream.readline()
                server_time_start = time.time()  # record start time
                # print("recv: \'", rawData, '\'') if not production_env and rawData != '' else None

                From = rawData.replace(";", ":").split(": ")

                if "Phone" in From:

                    rawData = rawData.split("\n")[0]

                    for dataItem in [rawData]:

                        if dataItem != "":

                            if dataFlag:
                                # dataItem = dataTemp + dataItem
                                dataTemp = ""
                                dataFlag = False

                            data = dataItem.split("; ")
                            # print(data)
                            dbData = {

                            }
                            time_initial = -1
                            for item in data:
                                if item != "":
                                    items = item.split(": ")
                                    if (len(items) == 2 and items[1] != ""):
                                        # print(items[0])
                                        if (items[0] == "Time"):
                                            timestr = dbData['Time'] = items[1]

                                            # print(timestr)
                                            # duration = time.time() - float(timestr)
                                            # print(duration * 1000)
                                            # dbData['sentDurationMs'] = duration * 1000
                                        if (items[0] == "abstime"):
                                            time_initial = items[1]
                                            # print("found abstime")
                                        if (items[0] == "CSQ"):
                                            dbData['CSQ'] = items[1]
                                        if (items[0] == "Index"):
                                            dbData['Index'] = items[1]
                                            index_send = items[1]
                                        if (items[0] == "Height"):
                                            dbData['Height'] = items[1]
                                        if (items[0] == "Lati"):
                                            dbData['Latitude'] = items[1]
                                        if (items[0] == "Long"):
                                            dbData['Longitude'] = items[1]
                                        if (items[0] == "Network"):
                                            dbData['Network'] = items[1]

                            if Latitude != "":
                                dbData['Latitude'] = Latitude

                            if Longitude != "":
                                dbData['Longitude'] = Longitude
                            if Temperature != "":
                                dbData['Temperature'] = Temperature
                            if Height != "":
                                dbData['Height'] = Height
                            # print(dbData)

                            if (("Time" in dbData) and ("CSQ" in dbData) and ("Index" in dbData) and (
                                    "Height" in dbData) and ("Network" in dbData)):
                                if production_env:
                                    mongo = client.surf
                                    table = mongo[self.time]
                                    table.insert_one(dbData)
                                    print("send back")
                                    # print(dbData)

                                    newSend.add(index_send, server_time_start, time_initial)
                                else:
                                    # print("send back not production")
                                    # newSend = SendBackThread(conn2)
                                    # print(dbData)
                                    newSend.add(index_send, server_time_start, time_initial)

                            else:
                                print("did not trigger send-back")
                                print("data: ", dbData)
                                dataTemp = dataItem
                                dataFlag = True

                if "Module" in From:

                    isModule = True

                    data = rawData.split("; ")

                    for item in data:

                        items = item.split(": ")

                        if len(items) == 2:

                            if (items[0] == "Latitude" and items[1] != ""):
                                Latitude = items[1]

                            if (items[0] == "Longitude" and items[1] != ""):
                                Longitude = items[1]

                            if (items[0] == "Temperature" and items[1] != ""):
                                Temperature = items[1]

                            if (items[0] == "Height" and items[1] != ""):
                                Height = items[1]

                if rawData == "":
                    self.mySocket.shutdown(2)
                    self.mySocket.close()
                    self.iostream.close()
                    print("break")

                    # print("Connection Ended by Client\n")
                    # break

                # Start sending back data to Client(Phone)

                # Send ended


            except:
                try:
                    self.mySocket.shutdown(2)
                    self.mySocket.close()
                except:
                    pass
                # print(err)
                # print(rawData)


# Initialize sending and receiving sockets

tcps = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

tcps.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

tcps.bind((host, port_recv))



send_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)


send_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

send_socket.bind((host, port_send))



while True:
    tcps.listen(1)
    print("Waiting for incoming connections\n")
    tcps.listen(1)
    conn, addr = tcps.accept()
    print("recv socket accept")
    send_socket.listen(1)
    conn2, addr2 = send_socket.accept()
    print("send socket accept")



    dt = datetime.datetime.now()
    currentTime = dt.strftime('%m-%d %H:%M')

    print("incoming connection from: ", addr, "\n")
    newThread = myThread(conn, currentTime)
    newThread.start()

    newSend = SendBackThread(conn2)
    newSend.start()
