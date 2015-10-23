#!/usr/bin/python

import subprocess
import os
import sys
import time 

# reads from file descriptor, fd, and creates a list with triples
# (<time>, <bidderName>, <command>)
def parse_messages(fd):
  messages = []
  for i in fd:
    ln = i.strip().split()
    messages.append((int(ln[0]), ln[1], " ".join(ln[2:])))
  return messages

def parse_ports(fd):
  num_clients = int(fd.readline())
  ports = {}
  for i in range(num_clients):
    ln = fd.readline().split()
    ports[ln[0]] = ln[1]
  fd.readline()
  return ports

if len(sys.argv) != 2:
  print "Usage: " + sys.argv[0] + " [testcase]"
  exit()

# read the messages 
f = open(sys.argv[1],'r')
ports = parse_ports(f)
msgList = parse_messages(f)
f.close()

# creates a subprocess for the servers
with open ("logs/server.log", 'w') as fd: 
  with open ("logs/server.err", 'w') as fd2: 
    subprocess.Popen(["java", "ServerLauncher", "../auct_conf.txt"], stdout = fd, stderr = fd2)

# sleeps so as to wait for the 2 peers to connect
time.sleep(1)

# send the commands to the bidders
bidder = {}
last_time = 0
for i in msgList:
  time.sleep((i[0]-last_time)/1000.0)
  last_time = i[0]
  if i[2] == "launch":
    with open ("logs/{0}@localhost:{1}.out".format(i[1], ports[i[1]]), 'w') as fd: 
      with open ("logs/{0}@localhost:{1}.err".format(i[1], ports[i[1]]), 'w') as fd2:
        bidder[i[1]] = subprocess.Popen(
                         ["java", "ClientLauncher", "localhost", ports[i[1]], i[1]],
                         stdin = subprocess.PIPE, 
                         stdout = fd, 
                         stderr = fd2
                       )
  else: 
     bidder[i[1]].stdin.write(i[2] + "\n")

# wait for all the bidders to exit
for b in bidder.itervalues():
  b.wait()
