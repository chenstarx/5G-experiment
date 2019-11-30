import time
import socket
import datetime
import threading
from pymongo import MongoClient

client = MongoClient()

production_env = False

host = "0.0.0.0"
port_recv = 55800
port_send = 55801

class replyThread(threading.Thread):
    def __init__(self, socketInstance):
        threading.Thread.__init__(self)
        self.replySocket = socketInstance
        self.replySocket.settimeout(120)

    def close(self):
        self.replySocket.shutdown(2)
        self.replySocket.close()

    def reply(self, index_send, server_time_start, time_initial):
        try:
            server_duration = round((1000 * (time.time() - server_time_start)), 3)

            msg_send = "index: " + str(index_send) + "; server_process_time: " + \
                    str(server_duration) + "; client_time_start: " + str(time_initial) + "\n"

            self.replySocket.sendall(bytes(msg_send, 'utf-8'))
            print(msg_send)

        except Exception as err:
            self.close()
            print(err)


class receiveThread(threading.Thread):
    def __init__(self, socketInstance, startTime):
        threading.Thread.__init__(self)
        self.tableName = startTime
        self.receiveSocket = socketInstance
        self.receiveSocket.settimeout(120)
        self.iostream = self.receiveSocket.makefile('r')

    def close(self):
        self.receiveSocket.shutdown(2)
        self.receiveSocket.close()
        self.iostream.close()

    def run(self):

        global repThread

        while True:
            try:
                rawData = self.iostream.readline()

                server_time_start = time.time()
                
                if str(rawData) == "":
                    self.close()
                    repThread.close()
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
                                    if (items[0] == "Network"):
                                        dbData['Network'] = items[1]
                                    if (items[0] == "Speed"):
                                        dbData['Speed'] = items[1]

                        if production_env:
                            mongo = client.surf
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


# Initialize sending and receiving sockets

receiver = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

receiver.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

receiver.bind((host, port_recv))



replier = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

replier.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

replier.bind((host, port_send))


receiver.listen(1)

replier.listen(1)


while True:
    print("Waiting for incoming connections\n")

    conn1, addr1 = receiver.accept()
    print("receiver connected with:", addr1, "\n")

    conn2, addr2 = replier.accept()
    print("replier connected with:", addr2, "\n")

    dt = datetime.datetime.now()
    currentTime = dt.strftime('%m-%d %H:%M')

    recvThread = receiveThread(conn1, currentTime)
    recvThread.start()

    repThread = replyThread(conn2)
    repThread.start()
