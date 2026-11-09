#!/usr/bin/env python3
"""Local STARTTLS stand-in for the backup notification tests.

This process never opens a connection to SendGrid. It reads the expected
username and API key from the test env file and does not write them out.
"""

import base64
import json
import os
import socket
import ssl
import sys
import threading
import time

def read_expected(path):
    username = ""
    password = ""
    with open(path, "r", encoding="utf-8") as handle:
        for raw in handle:
            line = raw.strip()
            if line.startswith("SENDGRID_SMTP_USERNAME="):
                username = line.split("=", 1)[1]
            elif line.startswith("SENDGRID_API_KEY="):
                password = line.split("=", 1)[1]
    return username, password


def address_of(line):
    part = line.split(":", 1)[1].strip()
    if "<" in part and ">" in part:
        return part[part.index("<") + 1:part.index(">")]
    return part.split()[0].strip("<>")


class Stats:
    def __init__(self, path):
        self.path = path
        self.lock = threading.Lock()
        self.data = {
            "connections": 0,
            "auth": 0,
            "mail_from": [],
            "rcpt": [],
            "data": 0,
            "messages": [],
        }
        self.save()

    def save(self):
        temporary = self.path + ".tmp"
        with open(temporary, "w", encoding="utf-8") as handle:
            json.dump(self.data, handle)
        os.replace(temporary, self.path)

    def update(self, mutate):
        with self.lock:
            mutate(self.data)
            self.save()


class LineSocket:
    def __init__(self, sock):
        self.sock = sock
        self.buffer = b""

    def send(self, line):
        self.sock.sendall((line + "\r\n").encode("ascii"))

    def recvline(self):
        while b"\n" not in self.buffer:
            chunk = self.sock.recv(4096)
            if not chunk:
                return ""
            self.buffer += chunk
        line, self.buffer = self.buffer.split(b"\n", 1)
        return line.rstrip(b"\r").decode("utf-8", "replace")

    def wrap(self, context):
        self.sock = context.wrap_socket(self.sock, server_side=True)
        self.buffer = b""


def serve_client(connection, context, mode, username, password, stats):
    client = LineSocket(connection)
    try:
        client.send("220 ready")
        tls = False
        while True:
            line = client.recvline()
            if not line:
                break
            upper = line.upper()
            if upper.startswith("EHLO") or upper.startswith("HELO"):
                client.send("250-localhost")
                if not tls:
                    client.send("250-STARTTLS")
                client.send("250 AUTH PLAIN")
            elif upper == "STARTTLS":
                client.send("220 ready")
                client.wrap(context)
                tls = True
            elif upper.startswith("AUTH PLAIN"):
                parts = line.split(" ", 2)
                blob = parts[2] if len(parts) >= 3 else client.recvline()
                try:
                    fields = base64.b64decode(blob).split(b"\0")
                    given_user = fields[1].decode() if len(fields) > 1 else ""
                    given_password = fields[2].decode() if len(fields) > 2 else ""
                except Exception:
                    given_user = ""
                    given_password = ""
                stats.update(lambda item: item.__setitem__("auth", item["auth"] + 1))
                if mode == "auth" or given_user != username or given_password != password:
                    client.send("535 Authentication failed")
                else:
                    client.send("235 Authentication successful")
            elif upper.startswith("MAIL FROM:"):
                stats.update(lambda item, value=address_of(line): item["mail_from"].append(value))
                if mode == "sender":
                    client.send("550 Sender rejected")
                else:
                    client.send("250 Sender address accepted")
            elif upper.startswith("RCPT TO:"):
                stats.update(lambda item, value=address_of(line): item["rcpt"].append(value))
                if mode == "recipient":
                    client.send("550 Recipient rejected")
                else:
                    client.send("250 Recipient accepted")
            elif upper == "DATA":
                client.send("354 End data with <CR><LF>.<CR><LF>")
                body = []
                while True:
                    data_line = client.recvline()
                    if data_line == ".":
                        break
                    body.append(data_line)
                text = "\n".join(body)
                stats.update(lambda item, value=text: (
                    item.__setitem__("data", item["data"] + 1),
                    item["messages"].append(value),
                ))
                if mode == "data":
                    client.send("550 Delivery rejected")
                else:
                    client.send("250 Ok: queued as test-queue")
            elif upper == "QUIT":
                client.send("221 bye")
                break
            elif upper == "RSET":
                client.send("250 reset")
            else:
                client.send("500 unrecognized")
    finally:
        try:
            client.sock.close()
        except Exception:
            pass


def main():
    mode, cert, key, port_file, stats_file, puff_env = sys.argv[1:7]
    username, password = read_expected(puff_env)
    stats = Stats(stats_file)
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.load_cert_chain(cert, key)
    server = socket.socket()
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(("127.0.0.1", 0))
    server.listen(5)
    with open(port_file, "w", encoding="utf-8") as handle:
        handle.write(str(server.getsockname()[1]))

    def accept_loop():
        while True:
            try:
                connection, _peer = server.accept()
            except OSError:
                return
            stats.update(lambda item: item.__setitem__("connections", item["connections"] + 1))
            threading.Thread(
                target=serve_client,
                args=(connection, context, mode, username, password, stats),
                daemon=True,
            ).start()

    threading.Thread(target=accept_loop, daemon=True).start()
    try:
        while True:
            time.sleep(3600)
    except KeyboardInterrupt:
        pass
    finally:
        server.close()


if __name__ == "__main__":
    main()
