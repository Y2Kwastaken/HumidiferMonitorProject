import socket

HOST = "192.168.12.115"  # Standard loopback interface address (localhost)
PORT = 27015  # Port to listen on (non-privileged ports are > 1023)

with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
    s.bind((HOST, PORT))
    s.listen()
    print("Binded and listening on port", PORT, "for host", HOST)
    conn, addr = s.accept()
    with conn:
        print(f"Connected by {addr}")
        while True:
            conn.send(b'1111 1111')
