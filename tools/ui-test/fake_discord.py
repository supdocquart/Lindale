import socket, os, struct, json, sys
path=sys.argv[1]
if os.path.exists(path): os.unlink(path)
s=socket.socket(socket.AF_UNIX); s.bind(path); s.listen(1)
log=open(sys.argv[2],'a')
while True:
    c,_=s.accept()
    try:
        while True:
            h=c.recv(8)
            if len(h)<8: break
            op,n=struct.unpack('<ii',h); body=b''
            while len(body)<n: body+=c.recv(n-len(body))
            msg=json.loads(body); log.write(json.dumps({'op':op,'msg':msg})+'\n'); log.flush()
            if op==0: reply={'cmd':'DISPATCH','evt':'READY','data':{'v':1,'user':{'username':'test'}}}
            else: reply={'cmd':msg.get('cmd'),'evt':None,'nonce':msg.get('nonce'),'data':{}}
            b=json.dumps(reply).encode(); c.sendall(struct.pack('<ii',1,len(b))+b)
    except Exception as e: log.write('ERR %s\n'%e)
    c.close()
