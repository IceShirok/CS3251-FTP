package test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;

import util.ConvertLib;
import dtp.DTPSocket;

/**
 * Test server-side application.
 * 
 * @author Susanna Dong
 *
 */
public class ReceiverTest {

    /**
     * Test server-side application.
     * @param args
     */
    public static void main(String[] args) {
        try {
            DTPSocket serverSocket = new DTPSocket(8081, InetAddress.getLoopbackAddress(), 5000);
            serverSocket.window(1);
            serverSocket.listen();
            while (true) {
                DTPSocket socket = serverSocket.accept();
                System.out.println("IN RECEIVER TEST FINISHED GETTING CLIENT");

                long startTime = System.currentTimeMillis();
                byte[] buffer = new byte[3000];
                int temp = socket.recv(buffer);
                
                int size = Integer.parseInt(ConvertLib.convertBytesToString(buffer, temp));
                System.out.println("message length i got: " + size);

                String lorem = "";
                int length = 0;
                int bytesCopied = 0;
                while (bytesCopied < size) {
                    buffer = new byte[3000];
                    length = socket.recv(buffer);
                    bytesCopied += length;
                    lorem += ConvertLib.convertBytesToString(buffer, length);
                }
                long endTime = System.currentTimeMillis();
                System.out.println("sent a message:\n" + lorem);
                System.out.println("message length:\n" + lorem.length());
                System.out.println("transfer time: " + (endTime-startTime)/1000 + " s");

                socket.send(ConvertLib.convertStringToBytes("This test is to make sure the sockets"
                        + "\nare full-duplex. So far the tests have shown that they are,"
                        + "\nalthough I need to fix the timers. Seems like I need to use"
                        + "\nexternal timer, rather than rely on the datagram sockets."));
                socket.close();
            }
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
