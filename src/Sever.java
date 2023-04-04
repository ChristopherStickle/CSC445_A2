/*
CSC445 - Assignment 2
Christopher Stickle
DUE - 03/21/2023

This is the server side of the following assignment:

Write a proxy server program, that relays files/pages.
To demonstrate, you'll need a client and a server program:
The proxy server awaits connections.
A client connects, sends a URL.
The proxy server gets the corresponding page/file using HTTP.
The proxy server caches (immediately sending instead of fetching) at least the most recent request; optionally more.
The proxy sends the page/file to the client, that then displays it.
For purposes of this assignment, it is OK if the only kinds of files displayed are images (for example jpg).

Wherever applicable, use the commands and protocol for TFTP (IETF RFC 1350), with the following modifications.
You will need to design and use additional packet header information than that in TFTP;
use the IETF 2347 TFTP Options Extension when possible.

Use TCP-style sliding windows rather than the sequential acks used in TFTP.
Test with at least two different max window sizes.
Arrange that each session begins with a (random) number exchange to generate a key that is used for encrypting data.
You can just use Xor to create key, or anything better.
Support only binary (octet) transmission.
Support a command line argument controlling whether to pretend to drop 1 percent of the packets;
 */
import java.net.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Random;

import static java.nio.file.Files.*;


public class Sever {
    static final int PORT = 27050;
    static final int WINDOW_SIZE = 128;
    static final int TIMEOUT = 2000;
    static final String cachePath = "Cache/";
    /*
    ---------------------------------------------
    |   seqNum  |      data      |   checksum   |
    |  2 Bytes  |    512 Bytes   |   2 Bytes    |
    ---------------------------------------------
    */
    static final int SEQ_SIZE = 2;
    static final int DATA_SIZE = 1024;
    static final int CHECKSUM_SIZE = 2;
    static final int PACKET_SIZE = SEQ_SIZE + DATA_SIZE + CHECKSUM_SIZE;
    static final boolean DROP_PACKETS = false;
    static final int DROP_PERCENT = 1;
    static final int MAX_CONSECUTIVE_TIMEOUTS = 4;
    public static void main(String[] args) throws IOException {
        System.out.println("Server started");
        while (true) {
            try {
                DatagramSocket serverSocket = new DatagramSocket(PORT);
                //----------------------------------------------------------------------------------------------
                //Connection - Key Exchange - URL Exchange |
                //------------------------------------------
                // Wait for a connection
                System.out.println("Waiting for connection...");
                DatagramPacket packet = new DatagramPacket(new byte[PACKET_SIZE], PACKET_SIZE);
                serverSocket.receive(packet);
                InetAddress clientAddress = packet.getAddress();
                int clientPort = packet.getPort();
                System.out.println("Connection from " + clientAddress + ":" + clientPort);
                System.out.println("Client says: " + new String(packet.getData()));
                // say hello to the client
                packet = new DatagramPacket("hello".getBytes(), "hello".getBytes().length, clientAddress, clientPort);
                serverSocket.send(packet);
                // get the KEY
                byte[] keyBytes = new DatagramPacket(new byte[PACKET_SIZE], PACKET_SIZE).getData();
                serverSocket.receive(new DatagramPacket(keyBytes, PACKET_SIZE));
                int key = ByteBuffer.wrap(keyBytes).getInt();
                System.out.println("Key: " + key);
                // get the URL
                byte[] urlBytes = new DatagramPacket(new byte[PACKET_SIZE], PACKET_SIZE).getData();
                serverSocket.receive(new DatagramPacket(urlBytes, PACKET_SIZE));
                String url = new String(urlBytes, StandardCharsets.UTF_8);
                url = url.substring(0, url.indexOf('\0'));
                System.out.println("URL: " + url);
                //----------------------------------------------------------------------------------------------
                // Cache Check - File Fetch - File Size Check |
                //---------------------------------------------
                // check to see if the file is in the cache
                // if so fetch the file from the cache to send
                // if not, fetch the file from the URL and save it to the cache
                String fileName = url.substring(url.lastIndexOf('/') + 1);
                fileName = fileName.replace(":", "_");
                Path filePath = Path.of(cachePath + fileName);
                File file = new File(String.valueOf(filePath));
                if (!file.exists()) {
                    System.out.println("File not in cache, fetching from URL...");
                    fetchFile(url, fileName);
                }
                byte[] fileBytes = readAllBytes(filePath);
                System.out.println("File size: " + fileBytes.length + " bytes");
                //----------------------------------------------------------------------------------------------
                // determine how many frames are needed to send the file |
                //--------------------------------------------------------
                /*
                Must fit in 2^16 - 1 = 65535 Frames
                65535 Frames * 1024 Bytes = 67107840 Bytes = 67.107840 MB
                33.55392 MB is the max file size that can be sent without
                 cycling back to 0 for the sequence number.
                */
                // check file size, if it is too big, send an error message
                /*if(fileBytes.length > 67107840){
                    System.out.println("[X] ERROR - File is too large to send!");
                    // send the error message and close the connection
                    outStream.writeUTF("ERROR - File is too large to send!");
                    outStream.writeUTF("Closing connection...");
                    client.close();
                    inStream.close();
                    outStream.close();
                    continue;
                }*/
                //----------------------------------------------------------------------------------------------
                // encrypt the data |
                //-------------------
                fileBytes = xor(fileBytes, key);
                //----------------------------------------------------------------------------------------------
                // determine how many frames will be needed to send the file |
                //------------------------------------------------------------
                int numFrames = (int) Math.ceil((double) fileBytes.length / DATA_SIZE);
                System.out.println("Number of frames to send: " + numFrames);
                // create the frames
                ArrayList<byte[]> frames = new ArrayList<>();
                for (int i = 0; i < numFrames; i++) {
                    ByteBuffer buf = ByteBuffer.allocate(SEQ_SIZE + DATA_SIZE + CHECKSUM_SIZE);
                    // add the sequence number, using the first 2 bytes
                    buf.putShort((short) i);
                    // add the data, using the next 512 bytes
                    if (i < numFrames - 1) {
                        buf.put(fileBytes, i * DATA_SIZE, DATA_SIZE);
                    } else {
                        buf.put(fileBytes, i * DATA_SIZE, fileBytes.length - (i * DATA_SIZE));
                    }
                    // add the checksum, using the last 2 bytes
                    byte[] data = new byte[DATA_SIZE];
                    buf.position(SEQ_SIZE);
                    buf.get(data, 0, DATA_SIZE);
                    buf.putShort(calculateChecksum(data));
                    // add the frame to the frame array
                    frames.add(buf.array());
                }
                // send the number of frames to the client and wait for an ACK
                System.out.println("Sending number of frames...");
                serverSocket.send(new DatagramPacket(ByteBuffer.allocate(4).putInt(numFrames).array(), 4, clientAddress, clientPort));
                byte[] numOfFrameBytes = new byte[4];
                serverSocket.receive(new DatagramPacket(numOfFrameBytes, 4));
                System.out.println("Received ACK: " + ByteBuffer.wrap(numOfFrameBytes).getInt());
                //----------------------------------------------------------------------------------------------
                // send the file to the client using GBN sliding window |
                //-------------------------------------------------------
                System.out.println("Sending file...");
                short seqNumBase = 0;
                byte[] ackBytes = new byte[SEQ_SIZE];
                Random rand = new Random();
                int timeoutCount = 0;
                while (true) {
                    // send the window
                    for (int i = seqNumBase; i < seqNumBase + WINDOW_SIZE && seqNumBase < numFrames; i++) {
                        // determine if the packet should be dropped
                        if (DROP_PACKETS && rand.nextInt(99) < DROP_PERCENT) {
                            System.out.println("Dropped packet: [" + i + "]");
                        } else {
                            // send the frame
                            serverSocket.send(new DatagramPacket(frames.get(i), frames.get(i).length, clientAddress, clientPort));
                            System.out.println("Sent packet: [" + i + "]");
                            // if the last frame is sent, break
                        }
                        if (i == numFrames - 1) {
                            break;
                        }
                    }
                    // receive one or more ACKs
                    try {
                        // set the timeout
                        serverSocket.setSoTimeout(TIMEOUT);
                        // read the ack, 2 bytes
                        ackBytes = new byte[SEQ_SIZE];
                        serverSocket.receive(new DatagramPacket(ackBytes, SEQ_SIZE));
                        timeoutCount = 0;
                        // convert the ack to a short
                        short ack = ByteBuffer.wrap(ackBytes).getShort();
                        System.out.println(" Received ACK " + ack);
                        // if the ack was in the window, move the window forward to the 1 + ack
                        if (ack >= seqNumBase && ack < seqNumBase + WINDOW_SIZE) {
                            seqNumBase = (short) (ack + 1);
                        }
                        // if the ack was the final frame, we're done
                        if (ack == (numFrames - 1)) {
                            break;
                        }
                    } catch (SocketTimeoutException e) {
                        // if the ack is not received in time, resend the window
                        timeoutCount++;
                        System.out.println("[X] ERROR - Timeout occurred, resending window...");
                        if (timeoutCount == MAX_CONSECUTIVE_TIMEOUTS) {
                            System.out.println("[X] ERROR - Maximum number of timeouts reached, closing connection...");
                            break;
                        }
                    }
                }
                System.out.println("File transfer complete!");
                //-------------------------------------------------------------------------------------------
                // close the connection |
                //-----------------------
                serverSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    ///----------------///
    /// HELPER METHODS ///
    ///----------------///
    private static void fetchFile(String url, String fileName) throws IOException {
        // gets the corresponding page/file using HTTP and saves it to the Cache folder
        URL urlObj = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();
        connection.setRequestMethod("GET");
        connection.connect();
        // get the file
        InputStream fis = connection.getInputStream();
        // save the file to the Cache folder
        FileOutputStream fos = new FileOutputStream(cachePath + fileName);
        byte[] buffer = new byte[1024];
        int bytesRead = 0;
        while ((bytesRead = fis.read(buffer)) != -1) {
            fos.write(buffer, 0, bytesRead);
        }
        fos.close();
    }
    private static byte[] xor(byte[] msg, int key) {
        byte[] xorMsg = new byte[msg.length];
        for (int i = 0; i < msg.length; i++) {
            xorMsg[i] = (byte) (msg[i] ^ key);
        }
        return xorMsg;
    }
    public static short calculateChecksum(byte[] data) {
        // Sum all the bytes in the data portion of the packet and return the one's complement
        short sum = 0;
        for (int i = 0; i < DATA_SIZE; i++) {
            sum += data[i];
        }
        return (short) ~sum;
    }
}