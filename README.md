# multi-server-chat-system
This is a project for COMP90015 Distributed System. Aim is to extend extra features to the multi-server Chat Server we made individually in Project 1.

_Features include:_
* Secure communication with SSL/TLS
* Handling the failure of remote chat server with HeartBeat signal using UDP
* Authentication with normal email/password and OAuth with Google API

## Dependencies
```
json-simple-1.1.1 => parse json (from lms)
args4j-2.33 => parse cmd line input (from lms)
(other google api jars are specified in Maven build file)
```
## Generate certificate
Use any desired tools, so long as it is named "chjq-keystore"

---

## Compile and Execute
1. Compile 3 packages into jar file based on its main, authserver, server, and client
2. Set up config file for server and authserver. 
  1. Set up authserver config file:
  ```
  auth  <ipaddress> <listeningport> <coordinationport>  local  authserver
  ```
  2. Set up server config file:
  ```
  <serverid>  <ipaddress> <listeningport> <coordinationport>  local chatserver
  ```
3. Run the jars. 
  1. Running authserver:
  ```
  java -jar authserver.jar -s <serverid> -l <config.txt>
  ```
  2. Running server:
  ```
  java -jar server.jar -s <serverid> -l <config.txt>
  ```
  3. Running client:
  ```
  java -jar client.jar -i <clientid> -n <serverip> -p <port>
  ```
4. Extra servers can be added remotely. Execution of server is the same, but config file needs to be changed. Existing server config needs to be added into the config file of the new server, change local to remote.




