#!/usr/bin/env python3
import socket, threading
from urllib.parse import urlsplit

HOST='127.0.0.1'
PORT=18080

def pipe(src, dst):
    try:
        while True:
            data = src.recv(65536)
            if not data:
                break
            dst.sendall(data)
    except OSError:
        pass
    try:
        dst.shutdown(socket.SHUT_WR)
    except OSError:
        pass

def relay(a, b):
    t1 = threading.Thread(target=pipe, args=(a, b), daemon=True)
    t2 = threading.Thread(target=pipe, args=(b, a), daemon=True)
    t1.start(); t2.start()
    while t1.is_alive() or t2.is_alive():
        t1.join(0.5); t2.join(0.5)
    for s in (a, b):
        try: s.close()
        except OSError: pass

def read_head(conn):
    data=b''
    while b'\r\n\r\n' not in data and len(data)<32768:
        chunk=conn.recv(4096)
        if not chunk:
            raise ValueError('eof')
        data+=chunk
    pos=data.find(b'\r\n\r\n')
    return data[:pos+4], data[pos+4:]

def filtered_headers(raw):
    lines=raw.decode('latin1','replace').split('\r\n')
    out=[]
    for line in lines[1:]:
        if not line: continue
        name=line.split(':',1)[0].strip().lower()
        if name in ('proxy-connection','connection'):
            continue
        out.append(line)
    return out

def handle(conn,addr):
    remote=None
    try:
        conn.settimeout(30)
        head,rest=read_head(conn)
        first=head.split(b'\r\n',1)[0].decode('latin1','replace')
        parts=first.split()
        if len(parts)<2:
            conn.close(); return
        method=parts[0].upper()
        target=parts[1]

        if method=='CONNECT':
            hostport=target
            host,port=hostport.rsplit(':',1)
            host=host.strip('[]')
            remote=socket.create_connection((host,int(port)),timeout=20)
            conn.sendall(b'HTTP/1.1 200 Connection established\r\n\r\n')
            if rest:
                remote.sendall(rest)
        else:
            u=urlsplit(target)
            if u.scheme.lower()!='http' or not u.hostname:
                conn.sendall(b'HTTP/1.1 400 Bad Request\r\nConnection: close\r\n\r\nonly absolute http URLs or CONNECT supported')
                conn.close(); return
            port=u.port or 80
            remote=socket.create_connection((u.hostname,port),timeout=20)
            path=u.path or '/'
            if u.query: path+='?'+u.query
            lines=[f'{method} {path} {parts[2] if len(parts)>2 else "HTTP/1.1"}']+filtered_headers(head)
            outgoing=('\r\n'.join(lines)+'\r\n\r\n').encode('latin1','replace')+rest
            remote.sendall(outgoing)

        conn.settimeout(None)
        remote.settimeout(None)
        relay(conn,remote)
    except Exception as e:
        try:
            conn.sendall(('HTTP/1.1 502 Bad Gateway\r\nContent-Type: text/plain\r\nConnection: close\r\n\r\n'+str(e)).encode())
            conn.close()
        except Exception:
            pass
        if remote:
            try: remote.close()
            except Exception: pass

def main():
    srv=socket.socket(socket.AF_INET,socket.SOCK_STREAM)
    srv.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
    srv.bind((HOST,PORT))
    srv.listen(100)
    print(f'HTTP/CONNECT proxy listening on {HOST}:{PORT}',flush=True)
    while True:
        conn,addr=srv.accept()
        threading.Thread(target=handle,args=(conn,addr),daemon=True).start()

if __name__=='__main__':
    main()
