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
import java.util.ArrayList;

public class Sever {
    static final int PORT = 27050;
    static final int WINDOW_SIZE = 4;
    static final int TIMEOUT = 1000;
    public static void main(String[] args) {
        try {
            System.out.println("Server started");
            ServerSocket serverSocket = new ServerSocket(PORT);
            while (true) {
                long key = 1L;
                // Wait for a connection
                Socket client = serverSocket.accept();
                System.out.println("Connection from " + client.getInetAddress());
                // create in and out streams for the socket
                DataInputStream inStream = new DataInputStream(client.getInputStream());
                DataOutputStream outStream = new DataOutputStream(client.getOutputStream());
                // get the KEY
                key = inStream.readLong();
                // get the URL
                String url = inStream.readUTF();
                // gets the corresponding page/file using HTTP.
                URL urlObj = new URL(url);
                HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();
                connection.setRequestMethod("GET");
                connection.connect();
                // get the file
                InputStream file = connection.getInputStream();
                System.out.println("File size: " + file.available() + " bytes");
                // turn the file into a byte array
                byte[] fileBytes = new byte[file.available()];
                file.read(fileBytes);
                // determine how many frames are needed to send the file
                /*
                Must fit in 2^16 - 1 = 65535 Frames
                65535 Frames * 512 Bytes = 33553920 Bytes = 33.55392 MB
                33.55392 MB is the max file size that can be sent without
                 cycling back to 0 for the sequence number.
                ---------------------------------------------
                |   seqNum  |      data      |   checksum   |
                |  2 Bytes  |    512 Bytes   |   2 Bytes    |
                ---------------------------------------------
                */
                // check file size, if it is too big, send an error message
                if(fileBytes.length > 33554432){
                    System.out.println("[X] ERROR - File is too large to send!");
                    // send the error message and close the connection
                    outStream.writeUTF("ERROR - File is too large to send!");
                    outStream.writeUTF("Closing connection...");
                    client.close();
                    inStream.close();
                    outStream.close();
                    continue;
                }
                // encrypt the data
                //fileBytes = xor(fileBytes, key); //TODO: FIX THIS -- xor is not producing expected results
                // determine how many frames will be needed to send the file
                int numFrames = (int) Math.ceil((double) fileBytes.length / 512);
                System.out.println("Number of frames to send: " + numFrames);
                // create the frames
                ArrayList<byte[]> frames = new ArrayList<>();
                for(int i = 0; i < numFrames; i++){
                    ByteBuffer buf = ByteBuffer.allocate(516);
                    // add the sequence number, using the first 2 bytes
                    buf.putShort((short) i);
                    // add the data, using the next 512 bytes
                    if(i < numFrames-1){buf.put(fileBytes, i * 512, 512);}
                    else{buf.put(fileBytes, i * 512, fileBytes.length - (i * 512));}
                    // add the checksum, using the last 2 bytes
                    byte[] data = new byte[512];
                    buf.position(2);
                    buf.get(data, 0, 512);
                    buf.putShort(calculateChecksum(data));
                    // add the frame to the frame array
                    frames.add(buf.array());
                }
                // send the file to the client using GBN sliding window-----------------------------------------
                short seqNum = 0;
                byte[] ackBytes = new byte[2];
                while(true){
                    // send the window
                    for (int i = seqNum; i < seqNum + WINDOW_SIZE && seqNum<numFrames; i++) {
                        outStream.write(frames.get(i));
                        System.out.println("Sent packet " + i);
                        // if the last frame is sent, break
                        if(i == numFrames - 1){break;}
                    }
                    // receive one or more ACKs
                    try{
                        // set the timeout
                        client.setSoTimeout(TIMEOUT);
                        // read the ack, 2 bytes
                        inStream.read(ackBytes);
                        // convert the ack to an int
                        short ack = ByteBuffer.wrap(ackBytes).getShort();
                        System.out.println(" Received ACK " + ack);
                        // if the ack was in the window, move the window forward to the 1 + ack
                        if(ack >= seqNum && ack < seqNum + WINDOW_SIZE){
                            seqNum = (short) (ack + 1);
                        }
                        // if the ack was the final frame, we're done
                        if(ack == ( numFrames - 1)){break;}
                    }catch (SocketTimeoutException e){
                        // if the ack is not received in time, resend the window
                        System.out.println("[X] ERROR - Timeout occurred, resending window...");
                    }
                }
                System.out.println("File transfer complete!");
                //-------------------------------------------------------------------------------------------
                // close the connection
                client.close();
                inStream.close();
                outStream.close();
            }
        }
        catch (Exception e) {
            e.setStackTrace(e.getStackTrace());
            System.out.println(e);
        }
    }
    private static byte[] xor(byte[] msg, long key) {
        byte[] xorMsg = new byte[msg.length];
        for (int i = 0; i < msg.length; i++) {
            xorMsg[i] = (byte) (msg[i] ^ (key >> (i % 8)));
        }
        return xorMsg;
    }
    public static short calculateChecksum(byte[] data) {
        // Sum all the bytes in the data portion of the packet and return the one's complement
        short sum = 0;
        for (int i = 0; i < data.length; i++) {
            sum += data[i];
        }
        return (short) ~sum;
    }
    public static boolean isCorrupt(byte[] packet, short checksum) {
        return checksum != calculateChecksum(packet);
    }
}