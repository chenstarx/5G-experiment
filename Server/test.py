import time
import socket
import datetime
import threading
from pymongo import MongoClient

client = MongoClient()
production_env = True
host = "0.0.0.0"
port_recv = 55880
port_send = 55881

Latitude = ""
Longitude = ""
Temperature = ""
Height = ""
dataTemp = ""
dataFlag = False

isModule = False

tcps = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

tcps.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

tcps.bind((host, port_recv))



send_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)

send_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)

send_socket.bind((host, port_send))


# Initialized
tcps.listen(1)
print("Waiting for incoming connections\n")

conn, addr = tcps.accept()
print("recv socket accept")

send_socket.listen(1)
conn2, addr2 = send_socket.accept()
print("send socket accept")

exit(0)

# tcps = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
# tcps.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
#
# # 将socket绑定到ip和端口上
# tcps.bind((host, port_send))
#
# tcps_rev = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
# tcps_rev.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
#
# # 将socket绑定到ip和端口上
# tcps_rev.bind((host, port_recv))
#
# # 开始监听连接
# tcps.listen(1)
#
# print("Waiting for incoming connections\n")
#
# # 有连接请求后才到这一行
# conn, addr = tcps.accept()
# print("incoming connection from: ", addr, "\n")
#
# tcps_rev.listen(1)
# conn2, addr2 = tcps_rev.accept()
# print("incoming connection of rev from", addr)