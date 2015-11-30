package dtp;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import util.ConvertLib;

/**
 * Wrapper class of packet to manipulate data on a byte array.
 * @author Susanna Dong
 *
 */
public class Packet {

    private final static String ENCODING = "UTF-8";

    public final static byte MIN_HEADER_SIZE = 18;
    public final static short MAX_PACKET_SIZE = 1000 + MIN_HEADER_SIZE;
    public final static int MAX_PACKET_DATA_SIZE = MAX_PACKET_SIZE - MIN_HEADER_SIZE;

    private byte[] bytes;

    /**
     * Packet representation with methods that make setting bytes
     * on a byte array infinitely easier to do.
     * 
     * @param data the contents of the packet
     * @param isData set to true if data is payload, false if data is the raw packet
     */
    public Packet(byte[] data, boolean isData) {
        if (isData) {
            this.bytes = new byte[MAX_PACKET_SIZE];
            setHeaderLength(MIN_HEADER_SIZE);
            setData(data);
        } else {
            bytes = data;
        }
    }

    /**
     * Packet representation with no payload.
     */
    public Packet() {
        this(new byte[0], true);
    }

    public short getSrcPort() {
        return ConvertLib.convertBytesToShort(bytes[0], bytes[1]);
    }

    public void setSrcPort(short srcPort) {
        byte[] b = ConvertLib.convertShortToBytes(srcPort);
        bytes[0] = b[0];
        bytes[1] = b[1];
    }

    public short getDestPort() {
        return ConvertLib.convertBytesToShort(bytes[2], bytes[3]);
    }

    public void setDestPort(short destPort) {
        bytes[2] = (byte) (destPort >> 8);
        bytes[3] = (byte) (destPort);
    }

    public int getSeqNum() {
        return ConvertLib.convertBytesToInt(bytes[4], bytes[5], bytes[6], bytes[7]);
    }

    public void setSeqNum(int seqNum) {
        byte[] b = ConvertLib.convertIntToBytes(seqNum);
        bytes[4] = b[0];
        bytes[5] = b[1];
        bytes[6] = b[2];
        bytes[7] = b[3];
    }

    public int getAckNum() {
        return ConvertLib.convertBytesToInt(bytes[8], bytes[9], bytes[10], bytes[11]);
    }

    public void setAckNum(int ackNum) {
        byte[] b = ConvertLib.convertIntToBytes(ackNum);
        bytes[8] = b[0];
        bytes[9] = b[1];
        bytes[10] = b[2];
        bytes[11] = b[3];
    }

    public short getChecksum() {
        return ConvertLib.convertBytesToShort(bytes[12], bytes[13]);
    }

    public void setChecksum(short checksum) {
        bytes[12] = (byte) (checksum >> 8);
        bytes[13] = (byte) (checksum);
    }

    public short getWindowSize() {
        return ConvertLib.convertBytesToShort(bytes[14], bytes[15]);
    }

    public void setWindowSize(short windowSize) {
        bytes[14] = (byte) (windowSize >> 8);
        bytes[15] = (byte) (windowSize);
    }

    public boolean getSyn() {
        return ((bytes[16] >> 7) & 0x01) == 1;
    }

    public void setSyn(boolean syn) {
        bytes[16] = (byte) ((bytes[16] & 0x7f) | ((syn ? 1 : 0) << 7));
    }

    public boolean getChl() {
        return (bytes[16] >> 6 & 0x01) == 1;
    }

    public void setChl(boolean chl) {
        bytes[16] = (byte) (bytes[16] & 0xbf | (chl ? 1 : 0) << 6);
    }

    public boolean getAck() {
        return (bytes[16] >> 5 & 0x01) == 1;
    }

    public void setAck(boolean ack) {
        bytes[16] = (byte) (bytes[16] & 0xdf | (ack ? 1 : 0) << 5);
    }

    public boolean getFin() {
        return (bytes[16] >> 4 & 0x01) == 1;
    }

    public void setFin(boolean fin) {
        bytes[16] = (byte) (bytes[16] & 0xef | (fin ? 1 : 0) << 4);
    }

    public byte getHeaderLength() {
        return bytes[17];
    }

    public void setHeaderLength(byte headerLength) {
        bytes[17] = headerLength;
    }

    public byte[] getOptions() {
        return Arrays.copyOfRange(bytes, 18, getHeaderLength());
    }

    public void setOptions(byte[] options) {
        System.arraycopy(options, 0, bytes, 18, getHeaderLength() - 18);
    }

    public byte[] getData() {
        return Arrays.copyOfRange(bytes, getHeaderLength(), bytes.length);
    }

    public void setData(byte[] data) {
        System.arraycopy(data, 0, bytes, getHeaderLength(), data.length);
    }

    /**
     * Get the raw byte[] of the packet.
     * @return byte[] of the packet information (header and payload).
     */
    public byte[] getPacket() {
        return bytes;
    }

    /*
     * Run this to test packet functionality
     */
    public static void main(String[] args) throws UnsupportedEncodingException {
        short srcPort = 30000;
        short destPort = 15336;
        int seqNum = 1234567890;
        int ackNum = 2100000000;
        short checksum = 1111;
        short windowSize = 1111;
        boolean syn = false;
        boolean chl = false;
        boolean ack = true;
        boolean fin = true;
        String msg = "Hello world!";

        Packet packet = new Packet(msg.getBytes(ENCODING), true);
        packet.setSrcPort(srcPort);
        packet.setDestPort(destPort);
        packet.setSeqNum(seqNum);
        packet.setAckNum(ackNum);
        packet.setChecksum(checksum);
        packet.setWindowSize(windowSize);
        packet.setSyn(syn);
        packet.setChl(chl);
        packet.setAck(ack);
        packet.setFin(fin);

        System.out.println("--- test ---");
        System.out.print("srcPort:\t");
        System.out.println(srcPort == packet.getSrcPort());
        System.out.print("destPort:\t");
        System.out.println(destPort == packet.getDestPort());
        System.out.print("seqNum:\t\t");
        System.out.println(seqNum == packet.getSeqNum());
        System.out.print("ackNum:\t\t");
        System.out.println(ackNum == packet.getAckNum());
        System.out.print("checksum:\t");
        System.out.println(checksum == packet.getChecksum());
        System.out.print("windowSize:\t");
        System.out.println(windowSize == packet.getWindowSize());
        System.out.print("syn:\t\t");
        System.out.println(syn == packet.getSyn());
        System.out.print("chl:\t\t");
        System.out.println(chl == packet.getChl());
        System.out.print("ack:\t\t");
        System.out.println(ack == packet.getAck());
        System.out.print("fin:\t\t");
        System.out.println(fin == packet.getFin());
        System.out.print("headerLength:\t");
        System.out.println(MIN_HEADER_SIZE == packet.getHeaderLength());
        System.out.print("msg:\t\t");
        System.out.println(msg.equals(new String(packet.getData(), ENCODING)));
    }

}
