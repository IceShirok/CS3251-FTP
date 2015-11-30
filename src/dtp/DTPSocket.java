package dtp;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import util.ConvertLib;
import util.InvalidPortException;

/**
 * DTP Socket.
 * Provides reliable, connection-oriented, byte-stream data transfer.
 * 
 * @author Susanna Dong
 *
 */
public class DTPSocket {

    private final static int TIMEOUT = 1000;
    private final static int MAX_RETRY = 21;
    private final static TimeUnit TIME_UNIT = TimeUnit.MILLISECONDS;

    private DatagramSocket socket;
    private SocketState state;

    private InetAddress srcIp;
    private short srcPort;

    private short netEmuPort;
    private InetAddress destIp;
    private short destPort;
    private InetSocketAddress destAddr;

    private Random randy;
    private int seqNum;
    private int ackNum;

    private short maxWindowSize;

    // for server usage only
    private BlockingQueue<DTPSocket> requestQueue;
    private ConcurrentMap<InetSocketAddress, DTPSocket> connectedMap;
    private String challenge;

    private BlockingQueue<DatagramPacket> packets;
    private boolean isInHegemony;

    /**
     * Creates a DTPSocket binded to srcPort and interfacing with netEmuIp:netEmuPort.
     * 
     * @param srcPort the port number the DTPSocket will bind to
     * @param netEmuIp the IP address for NetEmu
     * @param netEmuPort the port number for NetEmu, 1 to 32,000
     * @throws IOException
     */
    public DTPSocket(int srcPort, InetAddress netEmuIp, int netEmuPort) throws IOException {
        if (!ConvertLib.isValidShort(srcPort)) throw new InvalidPortException(srcPort);
        if (!ConvertLib.isValidShort(netEmuPort)) throw new InvalidPortException(netEmuPort);

        this.srcIp = Inet4Address.getLocalHost();
        this.srcPort = (short)srcPort;
        this.destIp = netEmuIp;
        this.netEmuPort = (short)netEmuPort;

        this.packets = new LinkedBlockingQueue<>();
        this.socket = new DatagramSocket(new InetSocketAddress(srcPort));
        this.randy = new Random();
        this.seqNum = randy.nextInt(Integer.MAX_VALUE - 1) + 1;
        this.maxWindowSize = 1;     // default value is 1 (STOP AND WAIT)

        this.setState(SocketState.CLOSED);
    }

    /*
     * Overloaded constructor used for the server to created accept()ed clients.
     */
    private DTPSocket(DatagramSocket socky, int srcPort,
            InetAddress destIp, int destPort, int netEmuPort) throws IOException {
        this(srcPort, destIp, netEmuPort);
        this.socket = socky;
        this.destPort = (short) destPort;
        this.destAddr = new InetSocketAddress(this.destIp, this.netEmuPort);
        this.isInHegemony = true;
    }

    /**
     * Adjusts the receiving window size for the socket. windowSize
     * is valued in "chunks" of packet size determined in PAcket.java.
     * Note in serverSocket, sockets returned by accept() will inherit the window
     * size of serverSocket. accept()ed socket window sizes can be configured
     * independently of its serverSocket.
     * @param windowSize 1 - 32,000
     * @return true if windowSize is set correctly, false otherwise.
     */
    public boolean window(int windowSize) {
        if (!ConvertLib.isValidShort(windowSize)) {
            return false;
        }
        this.maxWindowSize = (short)windowSize;
        return true;
    }

    /**
     * Allows the socket to connect to the server via port number.
     * Cannot listen if connect() returns true on the socket.
     * 
     * @param destPort valued 1 to 32,000
     * @return true if the connection was successful, false otherwise.
     * @throws IOException
     * @throws InterruptedException
     */
    public boolean connect(int destPort)
            throws IOException, InterruptedException {
        if (!ConvertLib.isValidShort(destPort)) throw new InvalidPortException(destPort);
        if (this.getState().equals(SocketState.LISTENING)) {
            throw new SocketException("Cannot connect with a listening socket!");
        }
        this.destPort = (short)destPort;
        this.destAddr = new InetSocketAddress(destIp, netEmuPort);

        Packet receivingPacket = null;
        byte[] sendingPacket = null;
        DatagramPacket msg;

        String answer = "";
        int numRetries = 0;

        boolean isReceived = false;
        boolean isAcked = false;

        // Loop that listens for incoming packets from the DatagramSocket
        // and delivers then into the receive buffer.
        Thread listening = new Thread() {
            public void run() {
                while (!socket.isClosed()) {
                    try {
                        DatagramPacket msg = new DatagramPacket(new byte[Packet.MAX_PACKET_SIZE], Packet.MAX_PACKET_SIZE);
                        socket.receive(msg);
                        if (!getState().equals(SocketState.CLOSED)) {
                            deliver(msg);
                        }
                    } catch (IOException e) {
                        printDebug("EXCEPTION: " + e.getMessage() + " YARG!!!");
                        e.printStackTrace();
                    }
                }
            }
        };
        listening.start();

        do {
            try {
                if (!isReceived) {
                    printStatus("Connecting... Requesting connection from server...");
                    Packet synPacket = packetHelper();
                    synPacket.setSyn(true);
                    synPacket.setChecksum(calculateChecksum(synPacket));
                    sendingPacket = synPacket.getPacket();
                    msg = new DatagramPacket(sendingPacket, sendingPacket.length, this.destAddr);
                    this.send(msg);
                    this.setState(SocketState.CONNECTING);
                }

                else if (isReceived && !isAcked) {
                    printStatus("Request sent. Waiting for server response...");
                    Packet answerPacket = packetHelper(ConvertLib.convertStringToBytes(answer));
                    answerPacket.setChl(true);
                    answerPacket.setAck(true);
                    answerPacket.setChecksum(calculateChecksum(answerPacket));
                    sendingPacket = answerPacket.getPacket();
                    msg = new DatagramPacket(sendingPacket, sendingPacket.length, this.destAddr);
                    this.send(msg);
                }

                msg = this.recv();
                receivingPacket = createPacketReceived(msg.getData());

                if (!isReceived && receivingPacket.getChl()) {
                    printStatus("Received challenge from the server");
                    this.setSeqNum(receivingPacket.getAckNum());
                    this.setAckNum(receivingPacket.getSeqNum()+1);

                    this.challenge = ConvertLib.convertBytesToString(receivingPacket.getData(), 32);
                    answer = answerChallenge(srcIp, 0, this.challenge);
                    if (answer == null) throw new IOException("Issue with encoding...");

                    this.destPort = receivingPacket.getSrcPort();
                    this.setState(SocketState.CHALLENGED);
                    isReceived = true;
                    numRetries = 0;
                }

                else if (isReceived
                        && receivingPacket.getAck() && receivingPacket.getSyn()
                        && this.getAckNum() == receivingPacket.getSeqNum()) {
                    printStatus("Connected to the server!");
                    this.setSeqNum(this.getSeqNum() + 1);
                    this.setState(SocketState.ESTABLISHED);
                    isAcked = true;
                    numRetries = 0;
                }
            } catch(SocketTimeoutException e) {
                numRetries++;
                if (numRetries <= MAX_RETRY) {
                    printDebug("Socket has timed out! Retrying..." + "(" + numRetries + "/" + MAX_RETRY + ")");
                } else {
                    this.setState(SocketState.CLOSED);
                    throw new SocketTimeoutException("Socket timed out. Connection not established.");
                }
            }
        } while (!isAcked);
        this.printSeqNums();

        return true;
    }

    /**
     * Allows the socket to listen for incoming clients requesting connections.
     * Cannot connect if listen() succeeds on the socket.
     * @throws IOException
     */
    public void listen() throws IOException {
        this.setState(SocketState.LISTENING);

        this.requestQueue = new LinkedBlockingQueue<>();
        this.connectedMap = new ConcurrentHashMap<>();

        // Loop that listens for incoming clients
        // and also does some edge case handling
        Thread listening = new Thread() {
            public void run() {
                while (!socket.isClosed()) {
                    try {
                        DatagramPacket msg = new DatagramPacket(new byte[Packet.MAX_PACKET_SIZE], Packet.MAX_PACKET_SIZE);
                        socket.receive(msg);
                        if (!isChecksumGood(msg)) continue;

                        Packet packet = createPacketReceived(msg.getData());
                        SocketAddress addr = new InetSocketAddress(msg.getAddress(), packet.getSrcPort());
                        DTPSocket socky = connectedMap.get(addr);

                        // adds requesting clients into the queue
                        if (state.equals(SocketState.LISTENING)
                                && packet.getSyn() && !packet.getAck()
                                && (socky == null || socky.getState().equals(SocketState.CLOSED))) {
                            int newSrcPort = 0;
                            do {
                                newSrcPort = randy.nextInt(32000) + 1;
                            } while (connectedMap.get(new InetSocketAddress(msg.getAddress(), newSrcPort)) != null);
                            DTPSocket sockSock = new DTPSocket(socket, newSrcPort,
                                    msg.getAddress(), packet.getSrcPort(), netEmuPort);
                            sockSock.setAckNum(packet.getSeqNum()+1);

                            requestQueue.add(sockSock);
                            printStatus("Adding client " + addr.toString() + " to the queue!");
                        }

                        if (socky != null) {
                            SocketAddress sendAddr = new InetSocketAddress(msg.getAddress(), netEmuPort);
                            // delivers message to the appropriate socket
                            if (!socky.getState().equals(SocketState.CLOSED)) {
                                socky.deliver(msg);
                            }

                            // if the client needs a FIN packet when the other endpt is closed
                            if (packet.getFin() && socky.getState().equals(SocketState.CLOSED)) {
                                byte[] sendingPacket = createFinPacket(msg.getAddress());
                                msg = new DatagramPacket(sendingPacket, sendingPacket.length, sendAddr);
                                socket.send(msg);

                                sendingPacket = createFinAckPacket(msg.getAddress());
                                msg = new DatagramPacket(sendingPacket, sendingPacket.length, sendAddr);
                                socket.send(msg);
                            }
                        }
                    } catch (IOException e) {
                        e.printStackTrace();
                        printDebug("EXCEPTION: " + e.getMessage() + " ARGH!!!");
                    }
                }
            }
        };
        listening.start();
    }

    /**
     * Allows the socket to accept clients requesting connections. Will block until a
     * client requests a connection. A connection will be established and will return
     * a DTPSocket that can talk to the client.
     * @return a socket that has a connection with the client.
     * @throws IOException
     * @throws InterruptedException
     */
    public DTPSocket accept() throws IOException, InterruptedException {
        if (!this.getState().equals(SocketState.LISTENING)) {
            throw new SocketException("Socket isn't listening!");
        }

        DTPSocket socky = null;
        while (socky == null) {
            socky = requestQueue.poll(TIMEOUT, TIME_UNIT);
            if (this.isClosed()) {
                return null;
            }
        }
        InetSocketAddress addr = new InetSocketAddress(socky.destIp, socky.destPort);
        connectedMap.put(addr, socky);
        printStatus("Accepting client " + addr.toString());

        socky.challenge = generateRandomString();
        DatagramPacket msg = null;

        Packet packet = packetHelper(ConvertLib.convertStringToBytes(socky.challenge));
        packet.setSrcPort(socky.srcPort);
        packet.setDestPort(socky.destPort);
        packet.setSeqNum(socky.getSeqNum());
        packet.setAckNum(socky.getAckNum());
        packet.setWindowSize(socky.maxWindowSize);
        packet.setChl(true);
        packet.setChecksum(calculateChecksum(packet, socky.destIp));
        byte[] sendingPacket = packet.getPacket();
        DatagramPacket chlMsg = new DatagramPacket(sendingPacket, sendingPacket.length, socky.destAddr);

        boolean isAnswerSent = false;
        int numRetries = 0;
        do {
            try {
                socky.send(chlMsg);
                printStatus("Sent challenge to the client");
                socky.setState(SocketState.CHALLENGING);
    
                msg = socky.recv();
                if (needToSendSynAck(socky, msg).equals(SynAckState.GOOD_ANSWER)) {
                    isAnswerSent = true;
                    socky.setState(SocketState.ESTABLISHED);
                    printStatus("Connection established with client");
                }
            } catch (SocketTimeoutException e) {
                numRetries++;
                if (numRetries <= MAX_RETRY) {
                    printDebug("Socket has timed out! Retrying..." + "(" + numRetries + "/" + MAX_RETRY + ")");
                } else {
                    socky.setState(SocketState.CLOSED);
                    throw new SocketTimeoutException("Socket timed out. Connection not established.");
                }
            }
        } while (!isAnswerSent);
        socky.printSeqNums();

        return socky;
    }

    /*
     * The logic for determining whether the server should accept the client
     * Also used if the client never saw the SYN-ACK packet
     */
    private enum SynAckState {GOOD_ANSWER, BAD_ANSWER, NOT_APPLICABLE}
    private boolean sentSynAck = false;
    private SynAckState needToSendSynAck(DTPSocket socky, DatagramPacket msg) throws IOException {
        Packet receivingPacket = createPacketReceived(msg.getData());
        if (receivingPacket.getChl() && receivingPacket.getAck()
                && ((socky.getAckNum() == receivingPacket.getSeqNum() && !socky.sentSynAck)
                        || (socky.getAckNum() > receivingPacket.getSeqNum() && socky.sentSynAck))) {
            String answer = ConvertLib.convertBytesToString(receivingPacket.getData(), 32);
            if (isAnswerValid(answer, socky.destIp, 0, socky.challenge)) {
                if (!socky.sentSynAck) {
                    printStatus("Received a good answer from the client!");
                    socky.setSeqNum(socky.getSeqNum()+1);
                    socky.setAckNum(socky.getAckNum()+1);
                    socky.sentSynAck = true;
                } else {
                    printStatus("Resending SYN ACK!");
                }

                Packet packet = packetHelper();
                packet.setSrcPort(socky.srcPort);
                packet.setDestPort(socky.destPort);
                packet.setSeqNum(socky.getSeqNum());
                packet.setAckNum(socky.getAckNum());
                packet.setWindowSize(socky.maxWindowSize);
                packet.setSyn(true);
                packet.setAck(true);
                packet.setChecksum(calculateChecksum(packet, socky.destIp));
                byte[] sendingPacket = packet.getPacket();

                msg = new DatagramPacket(sendingPacket, sendingPacket.length, socky.destAddr);
                socky.send(msg);
                printStatus("sent ACK to the client\n-----\n");
                return SynAckState.GOOD_ANSWER;
            } else {
                printStatus("Client answer does not match server answer!");
                return SynAckState.BAD_ANSWER;
            }
        }
        return SynAckState.NOT_APPLICABLE;
    }

    /**
     * Sends information in the buffer to the other endpoint.
     *
     * @param buffer
     * @return the number of bytes copied from the buffer.
     * @throws IOException
     * @throws InterruptedException
     */
    public int send(byte[] buffer) throws IOException, InterruptedException {
        printStatus("----- START SENDING INFO -----");

        if (buffer.length <= 0) throw new IOException("Sending buffer is 0!");

        Packet receivingPacket = null;
        byte[] sendingPacket = null;
        DatagramPacket msg = null;

        int size = buffer.length;
        int windowStart = 0;
        int windowSent = 0;
        int numPackets = size / Packet.MAX_PACKET_DATA_SIZE;
        if (size % Packet.MAX_PACKET_DATA_SIZE != 0) numPackets++;
        int bytesCopied = 0;

        int numRetries = 0;
        boolean lengthAcked = false;
        boolean isFinished = false;
        int fastRetransmitCount = 0;
        int byteRepeated = 0;
        int advertisedWindowSize = 0;

        int originalSeq = this.getSeqNum();
        int originalAck = this.getAckNum();

        long startTime = System.currentTimeMillis();

        Packet sizePacket = packetHelper(ConvertLib.convertIntToBytes(size));
        sizePacket.setChecksum(calculateChecksum(sizePacket));
        byte[] sizeBytes = sizePacket.getPacket();
        DatagramPacket sizePack = new DatagramPacket(sizeBytes, sizeBytes.length, this.destAddr);

        do {
            try {
                if (!lengthAcked) {
                    printDebug("SENDING SIZE PACKET OF SIZE ["+size+"]");
                    this.send(sizePack);
                }

                else {
                    int sendingStart = 0;
                    int sendingEnd = 0;
                    // if timed out or require a fast transmit, send the oldest
                    // non-ACKed packet.
                    if ((System.currentTimeMillis() - startTime > TIMEOUT * 2)
                            || fastRetransmitCount >= 3) {
                        sendingStart = windowStart / Packet.MAX_PACKET_DATA_SIZE;
                        fastRetransmitCount = 0;
                        sendingEnd = (windowStart + 1) > numPackets
                                ? numPackets
                                : windowStart + 1;
                        startTime = System.currentTimeMillis();
                    }
                    // send packets that haven't been sent yet
                    else {
                        sendingStart = windowSent / Packet.MAX_PACKET_DATA_SIZE;
                        sendingEnd = (windowStart + advertisedWindowSize) > numPackets
                                ? numPackets
                                : windowStart + advertisedWindowSize;
                    }

                    for (int i = sendingStart; i < sendingEnd; i++) {
                        int bytesStart = i * Packet.MAX_PACKET_DATA_SIZE;
                        int bytesEnd = (i+1) * Packet.MAX_PACKET_DATA_SIZE;
                        if (bytesEnd > size) {
                            bytesEnd = size;
                        }
                        windowSent += Packet.MAX_PACKET_DATA_SIZE;
                        if (windowSent > size) {
                            windowSent = size;
                        }
                        Packet packet = new Packet(Arrays.copyOfRange(buffer, bytesStart, bytesEnd), true);
                        packet.setSrcPort(this.srcPort);
                        packet.setDestPort(this.destPort);
                        packet.setSeqNum(originalSeq + bytesEnd);
                        packet.setAckNum(originalAck + i + 1);
                        packet.setWindowSize(this.maxWindowSize);
                        packet.setChecksum(calculateChecksum(packet));
                        sendingPacket = packet.getPacket();
                        msg = new DatagramPacket(sendingPacket, sendingPacket.length, this.destAddr);
                        this.send(msg);
                        startTime = System.currentTimeMillis();
                    }
                }

                msg = this.recv();
                receivingPacket = createPacketReceived(msg.getData());

                if (receivingPacket.getFin()) {
                    waitingToClose(msg);
                    return 0;
                }

                if (!needToSendSynAck(this, msg).equals(SynAckState.NOT_APPLICABLE)) continue;

                // if the receiver hasn't gotten the size packet
                if (!lengthAcked && receivingPacket.getAck()
                        && originalAck == receivingPacket.getSeqNum()) {
                    printDebug("SENDING SIZE PACKET OF SIZE ["+size+"]");
                    this.send(sizePack);
                }

                // getting ACK of length packet
                if (!lengthAcked && receivingPacket.getAck()
                        && originalAck + 1 == receivingPacket.getSeqNum()) {
                    printDebug("PREPARING TO SEND DATA ["+size+"]");
                    lengthAcked = true;
                    numRetries = 0;
                }

                // determining what got ACKed
                if (receivingPacket.getSeqNum() > originalAck) {
                    startTime = System.currentTimeMillis();
                    advertisedWindowSize = receivingPacket.getWindowSize();
                    int expectedByte = receivingPacket.getAckNum() - originalSeq;
                    if (expectedByte == byteRepeated) { 
                        fastRetransmitCount++;
                    } else {
                        byteRepeated = expectedByte;
                        numRetries = 0;
                    }
                    if (expectedByte > size) {
                        printDebug("DUN");
                        isFinished = true;
                    } else if (lengthAcked && expectedByte < size) {
                        printDebug("UH OH");
                        if (windowStart < expectedByte - Packet.MAX_PACKET_DATA_SIZE) {
                            windowStart = expectedByte - Packet.MAX_PACKET_DATA_SIZE;
                        }
                    } else if (lengthAcked && expectedByte == size) {
                        printDebug("SMALL");
                        if (windowStart < size - (size / Packet.MAX_PACKET_DATA_SIZE)) {
                            windowStart = size - (size / Packet.MAX_PACKET_DATA_SIZE);
                        }
                    }
                }
            } catch(SocketTimeoutException e) {
                numRetries++;
                if (numRetries <= MAX_RETRY) {
                    printDebug("Socket has timed out! Retrying..." + "(" + numRetries + "/" + MAX_RETRY + ")");
                } else {
                    throw new SocketTimeoutException("Socket timed out.");
                }
            }
        } while(!isFinished);

        this.setSeqNum(this.getSeqNum() + size + 1);
        this.setAckNum(this.getAckNum() + numPackets + 1);

        this.printSeqNums();
        printStatus("----- FINISH SENDING INFO -----");
        return bytesCopied;
    }


    /**
     * Receives information from the other endpoint and stores it in the buffer.
     * @param buffer
     * @return the number of bytes copied into the buffer. 
     * @throws IOException
     * @throws InterruptedException
     */
    public int recv(byte[] buffer) throws IOException, InterruptedException {
        printStatus("----- START RECEIVING INFO -----");

        if (buffer.length <= 0) throw new IOException("Receive buffer is 0!");

        Packet receivingPacket = null;
        byte[] sendingPacket = null;
        DatagramPacket msg = null;
        DatagramPacket sizeAckPack = null;
        int sizeAckAck = this.getAckNum();

        int expectedSeq = this.getSeqNum();
        int expectedAck = this.getAckNum();

        int size = 0;
        int numRetries = 0;

        boolean lengthAcked = false;
        boolean isFinished = false;

        int bytesCopied = 0;
        int windowStart = 0;
        int chunkSize = Packet.MAX_PACKET_DATA_SIZE;

        do {
            try {
                msg = this.recv();
                receivingPacket = createPacketReceived(msg.getData());

                if (receivingPacket.getFin()) {
                    waitingToClose(msg);
                    return 0;
                }

                if (!needToSendSynAck(this, msg).equals(SynAckState.NOT_APPLICABLE)) continue;

                // sending an ACK if the other endpt lost the last ACK for sending
                if (sizeAckAck > receivingPacket.getSeqNum()) {
                    Packet packet = packetHelper();
                    packet.setSeqNum(expectedSeq);
                    packet.setAckNum(expectedAck);
                    packet.setWindowSize((short)maxWindowSize);
                    packet.setAck(true);
                    packet.setChecksum(calculateChecksum(packet));
                    sendingPacket = packet.getPacket();
                    sizeAckPack = new DatagramPacket(sendingPacket, sendingPacket.length, this.destAddr);
                    this.send(sizeAckPack);
                }

                // sending the ACK for the size packet
                if (sizeAckAck == receivingPacket.getSeqNum()) {
                    if (!lengthAcked) {
                        byte[] sizeBytes = Arrays.copyOfRange(receivingPacket.getData(), 0, 4);
                        size = ConvertLib.convertBytesToInt(sizeBytes[0], sizeBytes[1], sizeBytes[2], sizeBytes[3]);

                        if (windowStart + chunkSize > size) {
                            chunkSize = size - windowStart;
                        }
                        expectedSeq += 1;
                        expectedAck += chunkSize;

                        Packet packet = packetHelper();
                        int windowSize = Math.min(this.maxWindowSize, this.maxWindowSize-packets.size());
                        windowSize = Math.max(windowSize, 1);
                        packet.setSeqNum(expectedSeq);
                        packet.setAckNum(expectedAck);
                        packet.setWindowSize((short)windowSize);
                        packet.setAck(true);
                        packet.setChecksum(calculateChecksum(packet));
                        sendingPacket = packet.getPacket();
                        sizeAckPack = new DatagramPacket(sendingPacket, sendingPacket.length, this.destAddr);
                    }
                    this.send(sizeAckPack);

                    printDebug("PREPARING TO RECEIVE DATA ["+size+"]");
                    lengthAcked = true;
                    numRetries = 0;
                }

                // determining whether the data sent is expected
                else if (lengthAcked) {
                    byte[] data = receivingPacket.getData();
                    if (expectedAck == receivingPacket.getSeqNum()) {
                        for (int i=windowStart; i<(windowStart + chunkSize) && i < buffer.length; i++) {
                            buffer[i] = data[i-windowStart];
                        }

                        expectedSeq += 1;
                        bytesCopied += chunkSize;
                        if (windowStart + chunkSize >= size) {
                            isFinished = true;
                            expectedAck += 1;
                        } else {
                            windowStart += chunkSize;
                            if (windowStart + chunkSize > size) {
                                chunkSize = size - windowStart;
                            }
                            expectedAck += chunkSize;
                        }
                        numRetries = 0;
                    }
    
                    Packet packet = new Packet(data, true);
                    packet.setSrcPort(srcPort);
                    packet.setDestPort(destPort);
                    packet.setSeqNum(expectedSeq);
                    packet.setAckNum(expectedAck);
                    int windowSize = Math.min(this.maxWindowSize, this.maxWindowSize-packets.size());
                    windowSize = Math.max(windowSize, 1);
                    packet.setWindowSize((short)windowSize);
                    packet.setAck(true);
                    packet.setChecksum(calculateChecksum(packet));
                    sendingPacket = packet.getPacket();
                    msg = new DatagramPacket(sendingPacket, sendingPacket.length, this.destAddr);
                    this.send(msg);
                }
            } catch (SocketTimeoutException e) {
                numRetries++;
                if (numRetries <= MAX_RETRY) {
                    printDebug("Socket has timed out! Retrying..." + "(" + numRetries + "/" + MAX_RETRY + ")");
                } else {
                    throw new SocketTimeoutException("Socket timed out.");
                }
            }
        } while(!isFinished);
        this.setSeqNum(expectedSeq);
        this.setAckNum(expectedAck);

        this.printSeqNums();
        printStatus("----- END RECEIVING INFO -----");
        return bytesCopied;
    }


    /**
     * Closes the socket's connection.
     * @throws IOException
     * @throws InterruptedException
     */
    public void close() throws IOException, InterruptedException {
        if (this.getState().equals(SocketState.CLOSED)) {
            throw new SocketException("Connection is already closed!");
        }
        if (this.getState().equals(SocketState.LISTENING)) {
            this.setState(SocketState.CLOSED);
            return;
        }
        waitingToClose(null);
    }

    /*
     * Helper method that pretty much is a finite state machine of connection teardown.
     * 
     * If the serverSocket is closing, the interfacing socket will not close unless
     * its accept()ed sockets are also closed. If not all are closed, the serverSocket
     * will simply stop listening for connection requests.
     */
    private void waitingToClose(DatagramPacket msg) throws IOException, InterruptedException {
        byte[] sendingPacket = null;
        int retryCount = 0;
        int timedWaitCount = 0;
        do {
            try {
                if (msg == null && this.getState().equals(SocketState.ESTABLISHED)) {
                    sendingPacket = createFinPacket(this.destIp);
                    this.setState(SocketState.FIN_WAIT_1);
                }

                if (sendingPacket != null) {
                    this.send(new DatagramPacket(sendingPacket, sendingPacket.length, this.destAddr));
                }

                if (msg == null && !this.getState().equals(SocketState.CLOSE_WAIT)) {
                    msg = this.recv();
                } else if (this.getState().equals(SocketState.CLOSE_WAIT)) {
                    sendingPacket = createFinPacket(this.destIp);
                    this.setState(SocketState.LAST_ACK);
                    continue;
                }

                Packet receivingPacket = createPacketReceived(msg.getData());
                if (receivingPacket.getFin()) {
                    if (this.getState().equals(SocketState.ESTABLISHED)) {
                        if (!receivingPacket.getAck()) {
                            sendingPacket = createFinAckPacket(this.destIp);
                            this.setState(SocketState.CLOSE_WAIT);
                        }
                    } else if (this.getState().equals(SocketState.FIN_WAIT_1)) {
                        if (!receivingPacket.getAck()) {
                            sendingPacket = createFinPacket(this.destIp);
                            this.setState(SocketState.CLOSING);
                        } else {
                            this.setState(SocketState.FIN_WAIT_2);
                        }
                    } else if (this.getState().equals(SocketState.FIN_WAIT_2)) {
                        if (!receivingPacket.getAck()) {
                            sendingPacket = createFinAckPacket(this.destIp);
                            this.setState(SocketState.TIMED_WAIT);
                        }
                    } else if (this.getState().equals(SocketState.CLOSING)) {
                        if (receivingPacket.getAck()) {
                            this.setState(SocketState.TIMED_WAIT);
                        } else {
                            sendingPacket = createFinAckPacket(this.destIp);
                        }
                    } else if (this.getState().equals(SocketState.LAST_ACK)) {
                        if (receivingPacket.getAck()) {
                            this.setState(SocketState.CLOSED);
                        } else {
                            sendingPacket = createFinPacket(this.destIp);
                        }
                    }
                }
                if (this.getState().equals(SocketState.TIMED_WAIT)) {
                    if (timedWaitCount >= 3) {
                        this.setState(SocketState.CLOSED);
                    } else if (receivingPacket.getFin()) {
                        sendingPacket = createFinAckPacket(this.destIp);
                    }
                    timedWaitCount++;
                }
                msg = null;
            } catch (SocketTimeoutException e) {
                if (this.getState().equals(SocketState.TIMED_WAIT)) timedWaitCount++;
                retryCount++;
                printDebug("Socket has timed out! Retrying..." + "(" + retryCount + "/" + MAX_RETRY + ")");
            }
        } while (!this.getState().equals(SocketState.CLOSED) && timedWaitCount < 3 && retryCount < 10);

        // server socket logic
        if (!isInHegemony) {
            socket.close();
        } else if (connectedMap != null) {
            boolean isEmpty = true;
            for (InetSocketAddress addr : connectedMap.keySet()) {
                if (!connectedMap.get(addr).isClosed()) {
                    isEmpty = false;
                }
            }
            if (isEmpty) {
                socket.close();
            }
        }
        printStatus("Client socket closed.");
    }


    /**
     * Determines whether a socket is closed or not by the socket state.
     * 
     * If the socket was a serverSocket, then it is considered closed
     * if all sockets from accept() are also closed.
     * 
     * @return true if the socket is closed, false otherwise.
     */
    public boolean isClosed() {
        boolean isClosed = this.getState().equals(SocketState.CLOSED);
        if (!isClosed) {
            return isClosed;
        } else if (this.connectedMap == null) {
            return isClosed;
        } else {
            for (InetSocketAddress addr : connectedMap.keySet()) {
                if (!connectedMap.get(addr).isClosed()) {
                    return false;
                }
            }
            return true;
        }
    }


    /*******************************************************
     * Helper methods to interface with packet receive buffers
     *******************************************************/
    /*
     * Sends the packet through the interfacing datagram socket
     */
    private void send(DatagramPacket packet) throws IOException {
        socket.send(packet);
    }

    /*
     * Receives a packet within an alloted time period at the receive buffer.
     * If the packet is corrupted or sent to the wrong port, the packet is
     * dropped. If the time exceeds the timeout period, the method throws
     * a SocketTimeoutException.
     */
    private DatagramPacket recv(int timeout) throws SocketTimeoutException, IOException, InterruptedException {
        DatagramPacket msg = null;
        boolean stopLooping = false;
        long startTime = System.currentTimeMillis();
        long endTime = System.currentTimeMillis();
        printDebug("BUFFER CONTAINS " + packets.size());
        do {
            synchronized (packets) {
                msg = packets.poll(timeout / 5, TIME_UNIT);
            }
            endTime = System.currentTimeMillis();
            if (msg == null && endTime - startTime >= timeout) {
                throw new SocketTimeoutException();
            }
            // test cases for whether the packet is good
            if (msg != null) {
                Packet p = createPacketReceived(msg.getData());
                if (this.destIp.equals(msg.getAddress()) && isChecksumGood(msg)
                        && ((this.srcPort == p.getDestPort() && this.destPort == p.getSrcPort())
                                || this.getState().equals(SocketState.CONNECTING))) {
                    stopLooping = true;
                }
            }
        } while (!stopLooping);
        printDebug("RECEIVING PACKET");
        return msg;
    }

    /*
     * Overloaded method that gets the DTPSocket's default timeout period.
     */
    private DatagramPacket recv() throws SocketTimeoutException, IOException, InterruptedException {
        return this.recv(TIMEOUT);
    }

    /*
     * If the socket's receive buffer is not full (determined by the windowSize),
     * then the packet is added to the receive buffer.
     */
    private void deliver(DatagramPacket packet) {
        synchronized (packets) {
            if (packets.size() < this.maxWindowSize) {
                packets.add(packet);
            }
        }
    }


    /*******************************************************
     * Packet creation helper methods
     *******************************************************/
    private Packet packetHelper(byte[] data) {
        Packet packet = new Packet(data, true);
        packet.setSrcPort(srcPort);
        packet.setDestPort(destPort);
        packet.setSeqNum(seqNum);
        packet.setAckNum(ackNum);
        packet.setWindowSize(maxWindowSize);
        return packet;
    }
    private Packet packetHelper() {
        return this.packetHelper(new byte[0]);
    }
    private byte[] createFinPacket(InetAddress addr) {
        Packet packet = packetHelper();
        packet.setFin(true);
        packet.setChecksum(calculateChecksum(packet, addr));
        return packet.getPacket();
    }
    private byte[] createFinAckPacket(InetAddress addr) {
        Packet packet = packetHelper();
        packet.setFin(true);
        packet.setAck(true);
        packet.setChecksum(calculateChecksum(packet, addr));
        return packet.getPacket();
    }
    private Packet createPacketReceived(byte[] data) {
        return new Packet(data, false);
    }


    /*******************************************************
     * Challenge and answer algorithms
     * See README for more details.
     *******************************************************/
    private static final String RAND_LIB = "1234567890qwertyuiopasdfghjklzxcvbnmQWERTYUIOPASDFGHJKLZXCVBNM";
    public String generateRandomString() {
        String aString = "";
        for(int i=0; i<32; i++) {
            aString += RAND_LIB.charAt(randy.nextInt(RAND_LIB.length()));
        }
        return aString;
    }
    private String answerChallenge(InetAddress address, int seqNum, String challenge) {
        try {
            return ConvertLib.hashString(address.getHostAddress() + seqNum + challenge);
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException e) {
            return null;
        }
    }
    private boolean isAnswerValid(String clientAnswer, InetAddress address, int seqNum, String challenge) {
        return clientAnswer.contains(answerChallenge(address, seqNum, challenge));
    }


    /*******************************************************
     * Checksum algorithms
     * Not in Packet.java because it requires knowledge of the IP address.
     * 
     * See README for details
     *******************************************************/
    private short calculateChecksum(Packet packet) {
        return calculateChecksum(packet, this.destIp);
    }
    private short calculateChecksum(Packet packet, InetAddress destIp) {
        byte[] src = this.srcIp.getAddress();
        byte[] dest = destIp.getAddress();
        short checksum = (short)(ConvertLib.convertBytesToShort(src[0], src[1])
                + ConvertLib.convertBytesToShort(src[2], src[3])
                + ConvertLib.convertBytesToShort(dest[0], dest[1])
                + ConvertLib.convertBytesToShort(dest[2], dest[3]));
        packet.setChecksum((short)0);
        byte[] data = packet.getPacket();
        for (int i=0; i<data.length; i+=2) {
            if (i == data.length - 1) {
                checksum += data[i];
            } else {
                checksum += (short)(ConvertLib.convertBytesToShort(data[i], data[i+1]));
            }
        }
        return checksum;
    }
    private boolean isChecksumGood(DatagramPacket msg) {
        Packet p = new Packet(msg.getData(), false);
        short expectedChecksum = p.getChecksum();
        InetAddress destIp = msg.getAddress();
        p.setChecksum((short)0);
        short actualChecksum = calculateChecksum(p, destIp);
        p.setChecksum(expectedChecksum);
        return expectedChecksum == actualChecksum;
    }


    /*******************************************************
     * Getters and setters
     *******************************************************/
    public InetAddress getSrcIp() {
        return srcIp;
    }
    public short getSrcPort() {
        return srcPort;
    }


    /*******************************************************
     * State getters and setters
     * Very useful for debugging
     *******************************************************/
    private void setState(SocketState state) {
        this.state = state;
        printDebug("STATE CHANGED TO : " + state.toString());
    }
    private SocketState getState() {
        return state;
    }


    /*******************************************************
     * Sequence and ACK number getters and setters
     *******************************************************/
    private int getSeqNum() {
        return seqNum;
    }
    private void setSeqNum(int seqNum) {
        this.seqNum = seqNum;
    }
    private int getAckNum() {
        return ackNum;
    }
    private void setAckNum(int ackNum) {
        this.ackNum = ackNum;
    }


    /*******************************************************
     * Debugging print statements
     *******************************************************/
    private boolean printDebug = true;      // print timeouts, exceptions, etc.
    private boolean printStatus = true;     // print state of method
    private boolean printSeqNums = true;    // print seq and ack numbers before and after send/recv
    private void printDebug(String msg) {
        if (printDebug) System.out.println("DEBUG: " + msg);
    }
    private void printStatus(String msg) {
        if (printStatus) System.out.println("STATUS: " + msg);
    }
    private void printSeqNums() {
        if (printSeqNums) {
            System.out.println("\nSEQ: " + getSeqNum()
                    + "\nACK: " + getAckNum() + "\n----");
        }
    }
}
