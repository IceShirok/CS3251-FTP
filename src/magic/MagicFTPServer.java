package magic;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;

import util.ConvertLib;
import util.InvalidPortException;
import dtp.DTPSocket;

public class MagicFTPServer {

    public final static int BUFFER_SIZE = 255;
    public final static int BIG_BUFFER_SIZE = 10000;

    /**
     * MagicFTP Server Application
     * 
     * @param args serverPort ipNetEmu portNetEmu
     */
    public static void main(String[] args) {
        // Input validation
        if (args.length >= 1 && args[0].equals("?")) {
            printHelp();
            System.exit(0);
        }
        if (args.length < 3 || args.length > 3) {
            System.out.println("Usage: java MagicFTPServer serverPort ipNetEmu portNetEmu");
            System.exit(1);
        }

        try {
            // More input validation
            InetAddress netEmuIp = InetAddress.getByName(args[1]);
            if (netEmuIp.equals(InetAddress.getLoopbackAddress())) {
                netEmuIp = InetAddress.getLocalHost();
            }
            int serverPort = Integer.parseInt(args[0]);
            int netEmuPort = Integer.parseInt(args[2]);
            if (!ConvertLib.isValidShort(serverPort)
                    || !ConvertLib.isValidShort(netEmuPort)
                    || serverPort % 2 == 0) {
                throw new InvalidPortException();
            }

            final DTPSocket serverSocket = new DTPSocket(serverPort, netEmuIp, netEmuPort);
            serverSocket.listen();

            Thread runny = new Thread() {
                public void run() {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    while (!serverSocket.isClosed()) {
                        try {
                            System.out.println("Server started. Waiting for clients...");
                            DTPSocket clientSocket = serverSocket.accept();

                            // will be null only if the server socket is closed
                            if (clientSocket == null) {
                                continue;
                            }
            
                            // Set-up to interact with client
                            String clientName = clientSocket.getSrcIp().toString()
                                    + ":" + clientSocket.getSrcPort();
                            System.out.println("Client " + clientName + " accepted!");
    
                            int length = 0;
                            String inMsg = "";
                            String outMsg = "";

                            // servicing the client
                            while (!clientSocket.isClosed()) {
                                try {
                                    length = clientSocket.recv(buffer);
                                    inMsg = ConvertLib.convertBytesToString(buffer, length);
                                    // System.out.println("Command found: " + inMsg);
    
                                    // skip if the message is nonsense
                                    if (inMsg == null || inMsg.isEmpty()) continue;
    
                                    // GET
                                    if (inMsg.substring(0, "GET ".length()).equals("GET ")) {
                                        String filename = inMsg.replace("GET ", "");
                                        System.out.println(clientName + " requests GET " + filename);
                                        File file = new File(filename);
                                        if (!file.exists()) {
                                            // file does not exist at the server side
                                            System.out.println("File " + filename + " does not exist! Denied...");
                                            outMsg = "FILE " + filename + " DOES NOT EXIST";
                                            clientSocket.send(ConvertLib.convertStringToBytes(outMsg));
                                        } else {
                                            // telling the client that the file exists
                                            System.out.println("File exists! Telling client...");
                                            outMsg = "FILE " + filename + " OK\nFILE SIZE " + file.length();
                                            clientSocket.send(ConvertLib.convertStringToBytes(outMsg));

                                            // getting the acknowledgment from the client before sending data
                                            buffer = new byte[BUFFER_SIZE];
                                            length = clientSocket.recv(buffer);
                                            inMsg = ConvertLib.convertBytesToString(buffer, length);
                                            // System.out.println("MSG: " + inMsg);

                                            // ready to upload the file to the client
                                            if (inMsg.equals("FILE " + filename + " READY")) {
                                                System.out.println("Ready to upload file to client " + clientName);
                                                DataInputStream reader = new DataInputStream(new FileInputStream(file));
                                                long count = 0;
                                                long fileSize = file.length();
                                                while (count < fileSize) {
                                                    buffer = new byte[BIG_BUFFER_SIZE];
                                                    int numCopied = reader.read(buffer);
                                                    buffer = Arrays.copyOfRange(buffer, 0, numCopied);
                                                    clientSocket.send(buffer);
                                                    count += numCopied;
                                                }
                                                System.out.println("File " + filename + " uploaded to " + clientName);
                                                reader.close();
                                            }
                    
                                        }
                                    }
                
                                    // POST
                                    else if (inMsg.substring(0, "POST ".length()).equals("POST ")) {
                                        String[] msgSplit = inMsg.split(" ");
                                        String srcFilename = msgSplit[1];
                                        String destFilename = msgSplit[3];

                                        // ask client the file size to prepare for downloading
                                        System.out.println("Requesting file information");
                                        buffer = ConvertLib.convertStringToBytes("FILE " + srcFilename + " OK REQUEST SIZE");
                                        clientSocket.send(buffer);

                                        // getting the file size
                                        buffer = new byte[BUFFER_SIZE];
                                        length = clientSocket.recv(buffer);
                                        inMsg = ConvertLib.convertBytesToString(buffer, length);
                                        String fileSizeTemp = inMsg.substring(inMsg.indexOf("FILE SIZE"), inMsg.length())
                                                .replace("FILE SIZE ", "");
                                        long fileSize = Long.parseLong(fileSizeTemp);

                                        // acknowledging client to send data
                                        buffer = ConvertLib.convertStringToBytes("FILE " + srcFilename + " READY");
                                        clientSocket.send(buffer);

                                        // downloading file from the client
                                        System.out.println("Uploading file " + srcFilename + " to the server.");
                                        DataOutputStream writer = new DataOutputStream(new FileOutputStream(destFilename));
                                        long count = 0;
                                        while (count < fileSize) {
                                            buffer = new byte[BIG_BUFFER_SIZE];
                                            int numCompied = clientSocket.recv(buffer);
                                            count += numCompied;
                                            writer.write(buffer, 0, numCompied);
                                        }
                                        System.out.println("File " + srcFilename + " was successfully copied.");
                                        writer.close();
                                    }
                                } catch (IOException e) {
                                    System.out.println("EXCEPTION: " + e.getMessage());
                                } catch (InterruptedException e) {
                                    System.out.println("EXCEPTION: " + e.getMessage());
                                }
                            }
                        } catch (InterruptedException e) {
                            System.out.println("EXCEPTION: " + e.getMessage());
                        } catch (IOException e) {
                            System.out.println("EXCEPTION: " + e.getMessage());
                        }
                    }
                    System.exit(0);
                }
            };
            runny.start();


            // Command usage on separate thread
            @SuppressWarnings("resource")
            Scanner scanny = new Scanner(System.in);
            System.out.println("Welcome to MagicFTP Server!");
            String art = " ._________________.\n"
                    + " | _______________ |\n"
                    + " | I             I |\n"
                    + " | I             I |\n"
                    + " | I             I |\n"
                    + " | I             I |\n"
                    + " | I_____________I |\n"
                    + " !_________________!\n"
                    + "    ._[_______]_.\n"
                    + ".___|___________|___.\n"
                    + "|::: ____           |\n"
                    + "|    ~~~~ [CD-ROM]  |\n"
                    + "!___________________!";
            System.out.println("\n" + art + "\nArt by AsciiWorld.com\n");
            ThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(10);
            while (true) {
                System.out.println("Type ? for access to commands.");
                System.out.print("Please type in a command. > ");
                String response = scanny.nextLine();
    
                // Terminating the server socket
                if (response.equals("terminate")) {
                    Thread req = new Thread() {
                        public void run() {
                            System.out.println("Terminating...");
                            try {
                                serverSocket.close();
                            } catch (IOException | InterruptedException e) {
                                System.out.println("EXCEPTION: " + e.getMessage() + " Exiting...");
                            }
                            System.out.println("Server terminated. (I will be back.)");
                        }
                    };
                    executor.execute(req);
                }

                // Adjusting the receiving window for the server socket.
                // All sockets that are accepted will have the same window size.
                else if (response.contains("window")) {
                    final String responseFinal = response;
                    Thread req = new Thread() {
                        public void run() {
                            try {
                                int windowSize = Integer.parseInt(responseFinal.split(" ")[1]);
                                serverSocket.window(windowSize);
                                System.out.println("Setting window size for file transfer to " + windowSize);
                            } catch (NumberFormatException e) {
                                System.out.println("EXCEPTION: " + e.getMessage() + " Must input a valid number!");
                            }
                        }
                    };
                    executor.execute(req);
                }
    
                // HELP
                else if (response.equals("?")) {
                    printHelp();
                }
    
                // OTHER STUFF THAT'S INVALID
                else {
                    System.out.println("Not a valid command.");
                    System.out.println("Type ? for help.");
                }
            }
        } catch (NumberFormatException | IOException e) {
            System.out.println("EXCEPTION: Please make sure values are valid formats.");
            System.out.println("EXCEPTION: That is, the port numbers are 1 to 2^31-1,"
                    + " the IP address is in valid dot notation, and the binding port"
                    + " is an even number.");
            System.out.println("EXCEPTION: Exiting...");
            System.exit(1);
        }
    }

    private static void printHelp() {
        System.out.println("\nValid commands");
        System.out.println("  window windowSize   Configure the window size of the TCP server.");
        System.out.println("  terminate           Shut down the server.");
    }
}
