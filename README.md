# CS3251 Programming Assignment 2 README

_Note: Best viewed in GitHub Markdown. https://jbt.github.io/markdown-editor/_

### By Susanna Dong
* sdong9@gatech.edu
* CS 3251 - Section A
* Due 27 November 2015

## Summary of CS3251 Project 2 Experience

ヽ(ﾟДﾟ)ﾉ        <- Me thinking of whether I should code with
                 a partner. (Nope.)

(╯°□°）╯︵ ┻━┻  <- Me trying to debug for several hours.

(┳Д┳)         <- Me after realizing the cause of the bug was
                 due to some very small line of code I changed.

o(╥﹏╥)o      <- Me questioning my abilities as a CS major and
                 my future life after college.

o(≧∇≦o)      <- Me after refactoring a bunch of code
                  and realizing it wasn't a waste of time.

(╯°□°）╯︵ ┻━┻  <- Me still trying to debug for several hours.

щ(゜ロ゜щ)       <- Me after getting dropped packet recovery hammered
                 down and seeing my FTP application running
                 successfully.

( ᵪ __ ᵪ  )   <- Me after turning in the project code.


## Table of Contents
* Filenames and Descriptions
* References
* Compiling and Running Files
  * What Java version for compiling and running?
  * How do I compile?
  * How do I run?
  * Example commands
* DTP Protocol
  * How does it take care of...?
  * DTP Packet Structure
    * Packet Size
    * Header Fields
  * DTP Connect Establishment and Shutdown
  * Checksum Algorithm
  * 4-Way Handshake Challenge Algorithm
  * Sequence and Acknowledgement Numbering
  * DTPSocket API
* MagicFTP Protocol
  * MagicFTP Messages
  * MagicFTP Client Commands and Usage
  * MagicFTP Server Commands and Usage
* Bugs and Limitations

_PLEASE read this before running stuff! There are some nuances and bugs
        in this project that may cause some errors that I have no idea why is
        happening. These occur randomly but rarely._


## Filenames and Descriptions
* doc
  * ```Sample.txt```: a file to test FTP.
  * Other files from Programming Assignment 1.
* src
  * dtp
    * ```DTPSocket.java```: DTP socket that provides reliable packet transfer.
    * ```Packet.java```: Wrapper class around a byte array to allow easy manipulation
      of data.
    * ```SocketState.java```: List of socket states, based on the FSM states.
  * magic
    * ```MagicFTPClient.java```: Client application for MagicFTP.
    * ```MagicFTPServer.java```: Server application for MagicFTP.
  * test
    * ```ReceiverTest.java```: Server-side testing class.
    * ```SenderTest.java```: Client-side testing class.
  * util
    * ```ConvertLib.java```: Common class for data type conversions.
    * ```InvalidPortException.java```: Exception thrown when an invalid port inputted.
* ```README.md```: what you're reading right now!


## References
* https://docs.oracle.com/javase/8/docs/api/
* http://rosettacode.org/wiki/MD5#Java
* http://wiki.ucalgary.ca/page/Courses/Computer_Science/CPSC_441.W2014/Chapter_3:_Transport_Layer
* http://www11.plala.or.jp/kita-kew/PC/images/NETWORK/Sliding_Window.GIF
* http://www.ccs-labs.org/teaching/rn/animations/gbn_sr/
* http://www.tcpipguide.com/free/t_TCPChecksumCalculationandtheTCPPseudoHeader2.
htm
* http://hexascii.com/, http://asciiworld.com
* CS3251 Project 1, CS3251 TAs


## Compiling and Running Files

### What Java version for compiling and running? What OS?
The project was created on the Eclipse IDE and complies with JDK 1.7.
The project was also compiled on JDK 1.8.0_51 and tested on JRE 1.8.0_66.
The project was developed on Windows 8 OS.

### How do I compile?

Before compiling, make sure there is a ```bin``` folder at the root directory.

To compile MagicFTP files, run the following command:

```shell
javac -d bin/ -cp src/ src/magic/*.java
```

To compile the test files, run the following command:

```shell
javac -d bin/ -cp src/ src/test/*.java
```

### How do I run?

To run the MagicFTP client, run the following command:

```shell
java -cp bin/ magic/MagicFTPClient clientPort ipNetEmu portNetEmu

java -cp bin/ magic/MagicFTPClient 8080 127.0.0.1 5000
```

To run the MagicFTP server, run the following command:

```shell
java -cp bin/ magic/MagicFTPServer serverPort ipNetEmu portNetEmu

java -cp bin/ magic/MagicFTPServer 8081 127.0.0.1 5000
```

To do a file transfer of ```Sample.txt``` (stored at ```doc```, assuming that the
client application is running on root folder) and store it to the server's root
directory, do the following:
  1. Run MagicFTP Server and MagicFTP Client with valid configurations.
  2. Connect MagicFTP Client to the server.
  3. Run the command ```post doc/Sample.txt lol.txt```.
  4. ???
  5. Profit.

To get a file from the server called ```Sample.txt``` (stored at ```doc```,
assuming that the server application is running on root folder) and store it to the
client's root directory, run ```get doc/Sample.txt Sample.txt```.

For other commands, see the MagicFTP API section.

## DTP Protocol
__From HW4__

The Defenestrating Transport Protocol (DTP) is a transport layer protocol
that provides reliable, connection-oriented, and byte-stream services
to endpoint systems!

DTP will act similarly to the Go-Back-N (GBN) protocol, with fast retransmit
to make the protocol more efficient. DTP will also provide a 4-way handshake
for connection establishment to provide better network security.

### How does it take care of...?
  * __Lost packets?__ DTP sockets have timers. If the sending socket does not
  receive a packet within a timeout period, the sender will retransmit its packet(s)
  and wait for a response from the receiver.
  * __Corrupted packets?__ DTP sockets utilizes a checksum algorithm to check
  whether the packet is corrupt or not. If the packet is bad according to the
  algorithm, the packet is dropped and continues its timeout period.
  DTP socket should treat this as a "lost" packet and act accordingly.
  * __Duplicate packets?__ DTP sockets utilizes sequence and acknowledge numbers,
  while are based upon the amount of data (in bytes) transferred. With non-data
  packets (such as SYN, CHL, ACK, FIN), these are assumed to hold 1 byte of data.
  For packets that are meant to transmit data, they hold n bytes of data, where n
  is the payload size. Duplicated packets are detected if the socket's ACK number
  does not match the packet's sequence number. The packet is dropped and the
  (for a sender) will retransmit a packet in response.
  * Out-or-order packets?__ Sequence and ACK numbers are also used to detect
  out-of-order packets. Out-of-order packets are treated similarly to duplicates.
  * __Bidirectional data transfers?__ Sender and receiver packets can
  simultaneously be data packets and acknowledgement packets. The packet will
  contain the segment number, as well as the acknowledgement number of the next
  packet the sender anticipates to receive. This can allow both sides to
  simultaneously send data and acknowledge received data. This also allows both
  endpoints to send and receive data to each other.
  * __Pipelining?__ Pipelining is handled by utilizing GBN as its sliding window
  protocol. Reference: http://www.ccs-labs.org/teaching/rn/animations/gbn_sr/
  It also performs "fast retransmit". If the sender detects receiving 3
  of the same ACKs, the sender will retransmit all unACKed packets in its window.
  The window is configurable.
  * __Flow control?__ The receiver will indicate in the packet header (via the
  window size header) of how much space is left in its receiving buffer. The sender
  will act accordingly and send at maximum the receiving window size. The sender and
  receiver can have non-matching sending vs. receiving windows.

### DTP Packet Structure

#### Packet Size

__Maximum packet size (header + data)__: 1000 + 18 bytes

_Note: for easier implementation, all packets sent are 1018 bytes in length.
This is because data length is not sent with the packet and packets can be sent
out-or-order, so the receiving socket is not aware of how long the packet will be.
However, the logic in the socket methods can determine the length of the packet
based on sequence numbers and flags._

__Minimum packet header size__: 18 bytes

#### Header Fields
* __Source port and destination port (16 bits each)__: the ports at which
the packet is sent from and sent to respectively.
* __Sequence number (32 bits)__: the sequence number of the packet.
* __Acknowledgement number (32 bits)__: the acknowledgement number of the packet.
This indicates the next sequence number the receiver anticipates in receiving.
* __Checksum (16 bits)__: a number calculated to see whether the packet
is corrupted or not.
* __Window size (16 bits)__: the size of the sender's receiving window. This
implementation does not use this header, as it uses GBN.
* __Header length (8 bits)__: the length of the header in bytes.
This implementation does not use this header, but this can be used for future
implementations.
* __Flags (8 bits)__: flags to raise. The current implementation has
the following flags:
  * __SYN__ : indicates that the packet is used for connection establishment.
  * __CHL__ : indicates that the data is used for connection authentication.
  * __ACK__ : indicates that the packet is acknowledging data.
  * __FIN__ : indicates that the packet is used for connection shutdown.
  * (empty, reserved 4 bits for future implementations)
* __Options (variable amount)__: an options field dependent on the header length.
For this implementation, the options field will not be used.
* __Data (variable amount)__: the payload of the packet.

### DTP Connect Establishment and Shutdown

__Establishment__
  1. Client wants to connect with server and sends a SYN.
  2. Assuming server is listening for incoming clients, server will create a
  challenge message, tag the message as CHL, and send it to the client.
  3. The client will answer the challenge (see Algorithms section for more details)
  and send the answer back as CHLACK.
  4. The server will compare the client’s answer with the server’s answer.
  If they match, the server sends an acknowledgement SYNACK and will deem
  the client worthy of transmitting data. Both server and client will have the
  connection established.
  5. If at any time, the client is unable to authenticate, the server will forget
  the client’s state and send a packet acknowledging that the connection was
  not established.

__Shutdown__: The FSM for shutdown is the same procedure as TCP.

### Checksum Algorithm
The checksum will be calculated by the following:
  1. Create a pseudoheader (consisting of the source IP address, destination IP
  address, and DTP packet length). Break down into 16-bit words and sum them
  together. Words that are less than 16 bits long will have 0’s padded on the left.
  Call this value ```pseudo_header_sum```.
  2. Break down the DTP header (excluding checksum) and data into 16-bit words
  and sum the words together. Words that are less than 16 bits long will have
  0’s padded on the left. Call this value ```dtp_packet_sum```.
  3. Let ```actual_checksum = pseudo_header_sum + dtp_packet_sum.```
  4. Let ```expected_checksum = checksum``` from the DTP packet header.
  5. If ```actual_checksum & (~ expected_checksum + 1) = 0```, then the packet
  is good. Otherwise, the packet is corrupted and is dropped.

### 4-Way Handshake Challenge Algorithm
During connection establishment, the client must prove to be a trusted client
wanting to connect. The 4-way handshake will be as followed:
  1. Client requests connection with the server by sending a SYN packet.
  2. Server sends a challenge via a 32-character string of random character in
  response and sends the information in a CHL packet. The server will keep the
  challenge string and  packet sequence number.
  3. Client must send an answer back to the server in order to authenticate.
  The answer must consist of the MD5 hash of the concatenation of the client’s
  IP address and the challenge string. ```answer = md5(clientIP+challenge)```
  4. Server must check if the challenge string is valid. The server will calculate its own answer by following Step 3 by its stored information for the client. If the answers match, the server will send a SYNACK. Otherwise, the server will
  drop the answer.

### Sequence and Acknowledgement Numbering
Sequence and acknowledgement numbers are used and numbered slightly differently from
TCP.
  * In connection establishment, sequence and acknowledgement numbers are incremented
  by the next packet anticipated, in 1 byte. This further allows the client and
  server to sync up, as well as add a little extra layer of security.
  * Connection teardown does not perform this procedure, since both endpoints will
  be shutting down anyway.
  * When sending/receiving data, the sender will first send a packet containing
  the length of the data. The receiver will acknowledge the length by setting
  its acknowledgement number as the next byte that it anticipates on seeing
  (exclusive). _For example, if the data size was 1500 split into 500 byte chunks,
  sender will send SEQ=125, PAYLOAD=1500. Receiver will acknowledge and send
  ACK=625. The next packet the sender should send is SEQ=625, PAYLOAD=0-624.
  The last acknowledgement from the receiver will be ACK=1501.
  This implementation makes it easier to parse data from packets, as well as
  easier synchronization with packets._
  * Packets are divided into "chunks" of bytes, where the maximum chunk size
  is the MSS of the packet, determined in ```Packet.java```. If the last packet
  of data does not divide evenly, then the size of the packet is dataLength % MSS.
  * When sending/receiving data, neither sender nor receiver will update their
  permanent sequence and acknowledgement numbers until they have successfully
  completed sending/receiving. This is to ensure that both endpoints are still
  in sync in case one endpoint results in failure.

### DTPSocket API

```java
DTPSocket(int srcPort, InetAddress netEmuIp, int netEmuPort)
```

Creates a ```DTPSocket``` binded to ```srcPort``` and interfacing with
```netEmuIp:netEmuPort```. The port number can be 1 to 32,000.
Will throw and exception if values are invalid, or an error occurs
while creating the socket.

```java
boolean window(int windowSize)
```

Adjusts the receiving window size for the socket. ```windowSize```
is valued in "chunks" of packet size determined in ```Packet.java```.
Note in serverSocket, sockets returned by ```accept()``` will inherit the window
size of serverSocket. accept()ed socket window sizes can be configured independently
of its serverSocket.
Will return true if ```windowSize``` is set correctly, false otherwise.

```java
boolean connect(int destPort)
```

Allows the socket to connect to the server via port number valued 1 to 32,000.
Cannot listen if ```connect()``` returns true on the socket.
Returns true if the connection was successful, false otherwise.
Will throw an exception if ```destPort``` is an invalid value,
or if an error occurs while connecting.

```java
void listen()
```

Allows the socket to listen for incoming clients requesting connections.
Cannot connect if ```listen()``` succeeds on the socket.
Will throw an exception if an error occurs while listening.

```java
DTPSocket accept()
```

Allows the socket to accept clients requesting connections. Will block until a
client requests a connection. A connection will be established and will return
a ```DTPSocket``` that can talk to the client.
Will throw an exception if an error occurs while accepting.

```java
int send(byte[] buffer)
```

Sends information in the ```buffer``` to the other endpoint.
Returns the number of bytes copied from the ```buffer```.
Will throw an exception if an error occurs while sending.

```java
int recv(byte[] buffer)
```

Receives information from the other endpoint and stores it in the ```buffer```.
Returns the number of bytes copied into the ```buffer```.
Will throw an exception if an error occurs while receiving.

```java
void close()
```

Closes the socket connection. For a serverSocket, the socket will no longer accept
incoming clients but will still be active if there are any outstanding accept()ed
clients still connected. Once all outstanding client connections are closed, the
serverSocket will close.
Will throw an exception if an error occurs while closing.


## MagicFTP Protocol
MagicFTP is a FTP application - as though done by magic! The FTP application
provides reliable file transfer. :D

While first running the application via command line:

  * The client will start but will not attempt connection unless the command has
  been inputted.
  * The server will automatically start listening for incoming clients. At any time,
  the user can type in commands.

### MagicFTP Messages

For ```GET srcFilename destFilename```:
  1. Client sends ```GET srcFilename```
  2. Server finds message and checks if ```srcFilename``` exists on the server.
  If the file exists, the server will send ```FILE srcFilename OK\nFILESIZE length```.
  The server waits for the client to acknowledge.
  3. Client sees that the file has been approved. It parses the ```length``` to
  prepare for file transfer. It sends ```FILE srcFilename READY``` to the server.
  4. Server acknowledges message. It begins to send the file in chunks until
  it sends the ```length``` amount of bytes.
  5. Client saves the file as ```destFilename```.

For ```POST srcFilename destFilename```:
  1. Client checks if ```srcFilename``` exists on its side. If the file does exist, it sends ```POST srcFilename TO destFilename``` to the server.
  2. Server finds message and will send ```FILE srcFilename OK REQUEST SIZE```.
  The server waits for the client to acknowledge to be able to anticipate the file
  size.
  3. Client sends ```FILE SIZE length``` to the server. It then waits for the server
  to acknowledge.
  4. Server finds the message and sends ```FILE srcFilename READY``` to the client.
  5. Client acknowledges message. It begins to send the file in chunks until
  it sends the ```length``` amount of bytes.
  6. Server saves the file as ```destFilename```.

### MagicFTP Client Commands and Usage

```shell
connect
```

Connects the client to the server.

```shell
window windowSize
```

Configures the DTP receiving buffer window size. See DTP specs for details.

```shell
get srcFilename destFilename
```

Downloads ```srcFilename``` from the server to the client at ```destFilename```.
Allows relative paths (relative to the application's location).
Spaces are not permitted in filenames.

```shell
post srcFilename destFilename
```

Uploads ```srcFilename``` from the client to the server at ```destFilename```.
Allows relative paths (relative to the application's location).
Spaces are not permitted in filenames.

```shell
disconnect
```

Shuts down the client.


### MagicFTP Server Commands and Usage

```shell
window windowSize
```

Configure the window size of the DTP socket receiving buffer window. Current
implementation changes the window size for the server and all future client sockets.
See DTP specs for more details.

```shell
terminate
```

Shuts down the server by refusing client connect requests. If there are any
outstanding client sockets because of ```accept()```, the server will not shut down
until all client sockets are also closed.


## Bugs and Limitations

### GBN Limitations
  Because DTP is based on a GBN protocol, dropped packets of any kind results
  in retransmitting packets. This may cause some packet spamming between sockets,
  which in turn will cause timeouts because the sockets can't process them fast
  enough!

### Bug where unable to connect because challenge unable to be set correctly.
  The DTP sockets should connect under non-ideal conditions. However, there is a
  bug where the sockets fail to make a connection because the challenge is always
  calculated as "bad", even though the parameters are correctly inputted.
  Just restart the client and server, and reconnect.

## Bug where MagicFTP messages get corrupted and results in an imaginary 1GB message.
  On rare occasions, the MagicFTP message sent will be corrupted and result in DTP
  sending a 1GB message that will never be sent. This occurrence can be seen while
  debugging is on in DTPSocket.

### Some difficulty typing commands in MagicFTP Server.
  _Note: This only applies while running debugging on DTPSocket._
  Because the command input and server processing run on different threads but
  same terminal, there may be some interruption typing in commands, especially when
  the server is waiting to receive a command after establishing a connection with
  the client. It is highly recommended to copy and paste your commands, rather than
  typing them in, or type your commands when the server is waiting to accept clients.

## No SEQ/ACK number overflow detection.
  The current implementation currently does not have detection of whether SEQ and ACK
  numbers have overflown.

## Changing window size for the server will not affect currently connected clients.
  If you change the window size on the server side while there is a connection,
  the server-side client socket will not change window size unless the connection is
  torn down and re-established. For example, if the serverSocket's window size is 5
  and accepts a client, the returnedSocket's window size is also 5. If the
  serverSocket window size is changed to 3, returnedSocket window size is still 5.
  This is a MagicFTP limitation, not a DTP limitation.

### No debug command via terminal.
  If you want to see debugging statements for DTP, you will need to toggle the
  boolean flags at the bottom of ```DTPSocket.java``` and re-compile.

### No timeout or retry setting via terminal.
  The following items can be toggled. This value is not changeable via the
  command line - this can only be changed via the source code.
  * Timeout duration in ```DtpSocket.java```
  * Max number of retries in ```DtpSocket.java```
  * Small buffer size (for small amounts of data to transfer in FTP)
  in ```MagicFTPServer.java```
  * Big buffer size (for large amounts of data to transfer in FTP)
  in ```MagicFTPServer.java```
  * Packet payload size in ```Packet.java```
  * _Note: the theoretical max window size can go up to 32,000 chunks, or
  32,000 * maxPacketPayload. However, windowSize may be limited by both the
  packet size determined in ```Packet.java``` and the big buffer size determined
  in ```MagicFTPServer.java```.

### Implementation was written with some NetEmu print statements removed.
  Due to the slowness of NetEmu, I have removed the print statement that prints
  the packet contents. If this causes issues with the code, it is recommended to
  lengthen the timeout period.
