import socket

HOST = "192.168.12.184"  # Standard loopback interface address (localhost)
PORT = 27015  # Port to listen on (non-privileged ports are > 1023)

sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
try:
    sock.connect((HOST, PORT))
    while True:
        message, ex = sock.recvfrom(4096)
        print(message)
except Exception as e:
    print("Cannot connect to the server:", e)
print("Connected")
