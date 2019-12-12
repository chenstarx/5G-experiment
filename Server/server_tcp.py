import time
import socket
import datetime
import threading
from pymongo import MongoClient

client = MongoClient()

production_env = True

host = "0.0.0.0"
port_recv = 55800
port_send = 55801

class replyThread(threading.Thread):
    def __init__(self, connInstance):
        threading.Thread.__init__(self)
        self.connection = connInstance
        self.connection.settimeout(120)

    def close(self):
        try:
            self.connection.shutdown(2)
            self.connection.close()
        except Exception as err:
            print(err)

    def reply(self, index_send, server_time_start, time_initial):
        try:
            server_duration = round((1000 * (time.time() - server_time_start)), 3)

            msg_send = str(index_send) + "; " + str(server_duration) + "; " + str(time_initial) + "; \n"

            self.connection.sendall(bytes(msg_send, 'utf-8'))

            print(msg_send)

        except Exception as err:
            self.close()
            print(err)


class receiveThread(threading.Thread):
    def __init__(self, connInstance, startTime):
        threading.Thread.__init__(self)
        self.tableName = startTime
        self.connection = connInstance
        self.connection.settimeout(120)
        self.iostream = self.connection.makefile('r')

    def close(self):
        try:
            global repThread
            self.iostream.close()
            self.connection.shutdown(2)
            self.connection.close()
            if not replyThread is None:
                repThread.close()
        except Exception as err:
            print(err)

    def run(self):
        while True:
            global repThread

            try:
                rawData = self.iostream.readline()

                server_time_start = time.time()
                
                if str(rawData) == "":
                    self.close()
                    print("Connection Ended by Client\n")
                    break

                print(rawData, '\n')

                rawData = rawData.split("\r\n")

                for dataItem in rawData:

                    if dataItem != "":

                        data = dataItem.split("; ")

                        dbData = {}

                        time_initial = -1
                        
                        for item in data:
                            if item != "":
                                items = item.split(": ")
                                if (len(items) == 2 and items[1] != ""):
                                    # print(items[0])
                                    if (items[0] == "Time"):
                                        dbData['Time'] = items[1]
                                    if (items[0] == "Timestamp"):
                                        time_initial = items[1]
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
                                    if (items[0] == "Speed"):
                                        dbData['Speed'] = items[1]
                                    if (items[0] == "Network"):
                                        dbData['Network'] = items[1].replace("\n", "")

                        if production_env:
                            mongo = client.drone
                            table = mongo[self.tableName]
                            table.insert_one(dbData)
                            repThread.reply(index_send, server_time_start, time_initial)
                        else:
                            # print(dbData)
                            repThread.reply(index_send, server_time_start, time_initial)

            except Exception as err:
                self.close()
                print(err)
                break


class watcherThread(threading.Thread):
    def __init__(self, socket, socketType):
        threading.Thread.__init__(self)
        self.socket = socket
        self.type = socketType
    
    def run(self):
        print("\nWaiting for incoming", self.type, "connection")
        while True:
            conn, addr = self.socket.accept()
            print(self.type, "connected with:", addr, "\n")

            if self.type == 'receiver':
                dt = datetime.datetime.now()
                currentTime = dt.strftime('%m-%d %H:%M')

                thread = receiveThread(conn, currentTime)
                thread.start()

            if self.type == 'replier':
                global repThread
                repThread = replyThread(conn)
                repThread.start()

# Initialize sending and receiving sockets

repThread = None

receiver = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
receiver.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
receiver.bind((host, port_recv))


replier = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
replier.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
replier.bind((host, port_send))


receiver.listen(1)
replier.listen(1)


receiverWatcher = watcherThread(receiver, 'receiver')
replierWatcher = watcherThread(replier, 'replier')


receiverWatcher.start()
replierWatcher.start()
