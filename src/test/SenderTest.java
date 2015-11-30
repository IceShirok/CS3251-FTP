package test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;

import util.ConvertLib;
import dtp.DTPSocket;

/**
 * Test client-side application.
 * 
 * @author Susanna Dong
 *
 */
public class SenderTest {

    /**
     * Test client-side application.
     * @param args
     */
    public static void main(String[] args) {
        try {
            DTPSocket socket = new DTPSocket(8080, InetAddress.getLoopbackAddress(), 5000);
            socket.window(1);
            socket.connect( 8081);

            String lorem = "";
            for (int i=0; i<3; i++) {
                lorem += "Lorem ipsum dolor sit amet, consectetur adipiscing elit."
                        + " Aliquam suscipit scelerisque nulla ullamcorper elementum."
                        + " Suspendisse non venenatis urna. Proin sodales eu lacus sit amet molestie."
                        + " In et risus non metus finibus suscipit. Proin vitae bibendum massa."
                        + " Proin efficitur luctus ipsum non commodo. Donec ornare eleifend lacus,"
                        + " sed eleifend justo maximus in. Integer condimentum turpis rutrum,"
                        + " molestie nisi vel, lacinia sem. Vestibulum varius, dui et tristique porta,"
                        + " tellus nisl pulvinar eros, eu suscipit enim purus vel velit."
                        + " Sed aliquet turpis ut leo tincidunt cursus."
                        + " Vivamus vel ipsum et neque dictum euismod.\n";
            }
            socket.send(ConvertLib.convertStringToBytes(lorem.length() + ""));
            System.out.println("DUN SUN");

            byte[] buffer = null;
            int chunk = 3000;
            int start = 0;
            int end = chunk;
            int bytesSent = 0;
            if (end >= lorem.length()) {
                end = lorem.length();
            }
            while (bytesSent < lorem.length()) {
                buffer = ConvertLib.convertStringToBytes(lorem.substring(start, end));
                System.out.println(lorem.substring(start, end));
                bytesSent += socket.send(buffer);
                start += chunk;
                end += chunk;
                if (end >= lorem.length()) {
                    end = lorem.length();
                    chunk = end - start;
                }
            }
            System.out.println("sent a message of length " + bytesSent);

            // testing bidirectional ability
            buffer = new byte[1500];
            int temp2 = socket.recv(buffer);
            String msg2 = ConvertLib.convertBytesToString(buffer, temp2);
            System.out.println("message i got:\n-----\n" + msg2 + "\n-----");
            socket.close();
        } catch (SocketException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
