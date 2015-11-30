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

import util.ConvertLib;
import util.InvalidPortException;
import dtp.DTPSocket;

public class MagicFTPClient {

    /**
     * MagicFTP Client Application
     * 
     * @param args clientPort ipNetEmu portNetEmu
     */
    public static void main(String[] args) {
        // input validation
        if (args.length >= 1 && args[0].equals("?")) {
            printHelp();
            System.exit(0);
        }
        if (args.length < 3 || args.length > 3) {
            System.out.println("Usage: java MagicFTPClient clientPort ipNetEmu portNetEmu");
            System.out.println("Type java MagicFTPClient ? for help.");
            System.exit(1);
        }

        int clientPort = 0;
        int netEmuPort = 0;
        InetAddress netEmuIp = null;
        DTPSocket clientSocket = null;
        try {
            netEmuIp = InetAddress.getByName(args[1]);
            if (netEmuIp.equals(InetAddress.getLoopbackAddress())) {
                netEmuIp = InetAddress.getLocalHost();
            }
            clientPort = Integer.parseInt(args[0]);
            netEmuPort = Integer.parseInt(args[2]);
            if (!ConvertLib.isValidShort(clientPort)
                    || !ConvertLib.isValidShort(netEmuPort)
                    || clientPort % 2 != 0) {
                throw new InvalidPortException();
            }
        } catch (NumberFormatException | IOException e) {
            System.out.println("EXCEPTION: Please make sure values are valid formats.");
            System.out.println("EXCEPTION: That is, the port numbers are 1 to 2^31-1,"
                    + " the IP address is in valid dot notation, and the binding port"
                    + " is an even number.");
            System.out.println("EXCEPTION: Exiting...");
            System.exit(1);
        }

        // creating the socket for communication :D
        try {
            clientSocket = new DTPSocket(clientPort, netEmuIp, netEmuPort);
        } catch (IOException e) {
            System.out.println("EXCEPTION: " + e.getMessage() + " Exiting...");
            System.exit(1);
        }

        // user input, as well as buffer sizes
        @SuppressWarnings("resource")
        Scanner scanny = new Scanner(System.in);
        String response = null;
        System.out.println("Welcome to MagicFTP!");
        String art = " .. .      I       ..    . ....\n"
                +" ..      ,'.`.         ... ..\n"
                +"   .__,-'.:::.`-.__,  ..  . \n"
                +".   ~-------------~\n"
                +"      _|=|___|=|_           .\n"
                +".__,-'.:::::::::.`-.__,  .\\/./\n"
                +" ~-------------------~   '\\|/`\n"
                +"    _|_|_|___|_|_|_       _|_\n";
        System.out.println("\n" + art + "\nArt by AsciiWorld.com\n");

        while (true) {
            System.out.println("Type ? for access to commands.");
            System.out.print("Please type in a command. > ");
            response = scanny.nextLine();

            // CONNECT
            if (response.equals("connect") && clientSocket.isClosed()) {
                try {
                    System.out.println("Connecting...");
                    clientSocket.connect(clientPort);
                    System.out.println("Connected.");
                } catch (IOException | InterruptedException e) {
                    System.out.println("EXCEPTION: " + e.getMessage());
                }
            }

            // DISCONNECT
            else if (response.equals("disconnect")) {
                try {
                    System.out.println("Disconnecting...");
                    if (!clientSocket.isClosed()) {
                        clientSocket.close();
                    }
                } catch (IOException | InterruptedException e) {
                    System.out.println("EXCEPTION: " + e.getMessage() + " Exiting...");
                }
                System.out.println("Disconnected from MagicFTP.");
                System.exit(0);
            }

            // WINDOW
            else if (response.contains("window")) {
                try {
                    int windowSize = Integer.parseInt(response.split(" ")[1]);
                    clientSocket.window(windowSize);
                    System.out.println("Setting window size for file transfer to " + windowSize);
                } catch (NumberFormatException e) {
                    System.out.println("EXCEPTION: " + e.getMessage() + " Must input a valid number!");
                }
            }

            // GET / DOWNLOAD
            else if (response.contains("get") && !clientSocket.isClosed()) {
                // making sure the user input is valid
                String[] responseString = response.split(" ");
                if (responseString.length == 3) {
                    String srcFilename = responseString[1];
                    String destFilename = responseString[2];

                    System.out.println("Getting file " + srcFilename + " from the server.");
                    long startTime = System.currentTimeMillis();
                    byte[] buffer;
                    try {
                        // request file from server
                        buffer = ConvertLib.convertStringToBytes("GET " + srcFilename);
                        clientSocket.send(buffer);
    
                        buffer = new byte[MagicFTPServer.BUFFER_SIZE];
                        int length = clientSocket.recv(buffer);
                        String msg = ConvertLib.convertBytesToString(buffer, length);
                        System.out.println("MSG: " + msg);

                        // acknowledgement from the server with file size
                        if (msg.contains("FILE " + srcFilename + " OK")) {
                            // get the file size from the msg
                            String fileSizeTemp = msg.substring(msg.indexOf("FILE SIZE"), msg.length())
                                    .replace("FILE SIZE ", "");
                            System.out.println("NUM NOT PARSED: " + fileSizeTemp);
                            long fileSize = Long.parseLong(fileSizeTemp);

                            // acknowledge server that client is ready to send
                            System.out.println("File exists at server. Giving thumbs-up to server.");
                            buffer = ConvertLib.convertStringToBytes("FILE " + srcFilename + " READY");
                            clientSocket.send(buffer);

                            // getting data from the server
                            DataOutputStream writer = new DataOutputStream(new FileOutputStream(destFilename));
                            System.out.println("Uploading file " + srcFilename + " to the server");
                            long count = 0;
                            while (count < fileSize) {
                                buffer = new byte[MagicFTPServer.BIG_BUFFER_SIZE];
                                int numCompied = clientSocket.recv(buffer);
                                count += numCompied;
                                writer.write(buffer, 0, numCompied);
                                System.out.printf("..... ( %d%% )\n", count * 100 / fileSize);
                            }
                            writer.close();

                            // post the time it took to transfer data
                            long endTime = System.currentTimeMillis();
                            System.out.println("File " + srcFilename + " was successfully copied.");
                            System.out.println("File " + srcFilename + " was " + fileSize + " bytes.");
                            System.out.println("File transfer took " + (endTime - startTime) + "ms.");
                        } else {
                            System.out.println("File " + srcFilename + " does not exist on the server!");
                        }
                    } catch (IOException | InterruptedException e) {
                        System.out.println("EXCEPTION: " + e.getMessage());
                    }
                } else {
                    printHelp();
                }
            }

            // PUT / UPLOAD
            else if (response.contains("post") && !clientSocket.isClosed()) {
                String[] responseString = response.split(" ");
                if (responseString.length == 3) {
                    String srcFilename = responseString[1];
                    String destFilename = responseString[2];
                    File file = new File(srcFilename);
                    if (!file.exists()) {
                        System.out.println("File " + srcFilename + " does not exist at the client!");
                    }
    
                    byte[] buffer;
                    long startTime = System.currentTimeMillis();
                    try {
                        // send uploading request to server
                        System.out.println("Request uploading file " + srcFilename + " to the server.");
                        buffer = ConvertLib.convertStringToBytes("POST " + srcFilename + " TO " + destFilename);
                        clientSocket.send(buffer);

                        // waiting for server to acknowledge and ask for file size information
                        buffer = new byte[MagicFTPServer.BUFFER_SIZE];
                        int length = clientSocket.recv(buffer);
                        String msg = ConvertLib.convertBytesToString(buffer, length);

                        if (msg.equals("FILE " + srcFilename + " OK REQUEST SIZE")) {
                            System.out.println("Sending upload information to the server.");
                            buffer = ConvertLib.convertStringToBytes("FILE SIZE " + file.length());
                            clientSocket.send(buffer);

                            // waiting for server to acknowledge data
                            buffer = new byte[MagicFTPServer.BUFFER_SIZE];
                            length = clientSocket.recv(buffer);
                            msg = ConvertLib.convertBytesToString(buffer, length);

                            // uploadind/sending information to the server
                            if (msg.equals("FILE " + srcFilename + " READY")) {
                                System.out.println("Uploading file " + srcFilename + " to the server");
                                DataInputStream reader = new DataInputStream(new FileInputStream(file));
                                long fileSize = file.length();
                                long count = 0;
                                while (count < fileSize) {
                                    buffer = new byte[MagicFTPServer.BIG_BUFFER_SIZE];
                                    int numCopied = reader.read(buffer);
                                    buffer = Arrays.copyOfRange(buffer, 0, numCopied);
                                    clientSocket.send(buffer);
                                    count += numCopied;
                                    System.out.printf("..... ( %d%% )\n", count * 100 / fileSize);
                                }
                                reader.close();

                                // post the time it took to transfer data
                                long endTime = System.currentTimeMillis();
                                System.out.println("Finished uploading the file "
                                        + srcFilename + " to the server.");
                                System.out.println("File " + srcFilename + " was " + fileSize + " bytes.");
                                System.out.println("File transfer took " + (endTime - startTime) + "ms.");
                            } else {
                                System.out.println("Uh-oh! The server may have died. :'(");
                            }
                        } else {
                            System.out.println("Server cannot get file " + srcFilename);
                        }
                    } catch (IOException | InterruptedException e) {
                        System.out.println("EXCEPTION: " + e.getMessage());
                    }
                } else {
                    printHelp();
                }
            }

            // HELP
            else if (response.equals("?")) {
                printHelp();
            }

            // OTHER STUFF THAT'S INVALID
            else {
                System.out.println("Not a valid command.");
                System.out.println("Make sure that you have properly connected to the server.");
                System.out.println("Type ? for help.");
            }

        }
    }

    private static void printHelp() {
        System.out.println("\nValid commands");
        System.out.println("  connect                         Connect the client to the server.");
        System.out.println("  window windowSize               Configure the window size of the TCP client.");
        System.out.println("  get srcFilename destFilename    Download filename from the server to the client.");
        System.out.println("  post srcFilename destFilename   Upload filename from the client to the server.");
        System.out.println("  disconnect                      Shut down the client.");
    }
}
