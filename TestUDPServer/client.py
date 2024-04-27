import socket

HOST = "192.168.12.184"
PORT = 27015
sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
try:
    sock.connect((HOST, PORT))
    while True:
        message, ex = sock.recvfrom(4096)
        print(message)
except Exception as e:
    print("Cannot connect to the server:", e)
print("Connected")
