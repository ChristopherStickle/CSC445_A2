/*
CSC445 - Assignment 2
Christopher Stickle
DUE - 03/21/2023

This is the Client side of the following assignment:

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
import java.util.Random;

public class Client {
    static final int PORT = 27050;
    String host = "localhost";
    static String url = "https://www.google.com/images/branding/googlelogo/2x/googlelogo_color_272x92dp.png";
    //REACT LOGO:   https://brandslogos.com/wp-content/uploads/images/large/react-logo-1.png
    //GOOGLE LOGO:  https://www.google.com/images/branding/googlelogo/2x/googlelogo_color_272x92dp.png


    public static void main(String[] args) {
        ArrayList<byte[]> file = new ArrayList<>();
        try {
            System.out.println("Client started");
            // create a socket
            Socket socket = new Socket("localhost", PORT);
            // create in and out streams for the socket
            DataInputStream inStream = new DataInputStream(socket.getInputStream());
            DataOutputStream outStream = new DataOutputStream(socket.getOutputStream());
            // create and send a random key
            long key = new Random().nextLong();
            outStream.writeLong(key);
            // send the URL
            outStream.writeUTF(url);
            /*
            ---------------------------------------------
            |   seqNum  |      data      |   checksum   |
            |  2 Bytes  |    512 Bytes   |   2 Bytes    |
            ---------------------------------------------
            */
            // get the file via sliding window go-back-n protocol ---------------------------------------------------
            short seqNum = -1; // the sequence number of the highest consecutive frame received; -1 is none received
            byte[] ack = new byte[2]; // the ack to send
            byte[] data = new byte[512]; // the data in the frame
            byte[] frame = new byte[516]; // the frame
            while (socket.isConnected()) {
                //listen for frame
                inStream.read(frame);
                // get the sequence number, and the data, and the checksum
                short frameSeqNum = ByteBuffer.wrap(frame, 0, 2).getShort();
                System.arraycopy(frame, 2, data, 0, 512);
                short checksum = ByteBuffer.wrap(frame, 514, 2).getShort();
                System.out.println("Received frame " + frameSeqNum);
                // check if the frame is corrupt
                if (isCorrupt(data, checksum)) {
                    System.out.println(" Frame " + frameSeqNum + " is corrupt -- Dropping frame");
                } else if (frameSeqNum == (1 + seqNum)) {
                    // if the frame is not corrupt, and it is the next frame in the sequence, add its data to the file
                    file.add(data);
                    // send an ack for the frame
                    ByteBuffer.wrap(ack).putShort(frameSeqNum);
                    outStream.write(ack);
                    System.out.println(" Looks good! : Sent ACK " + frameSeqNum);
                    seqNum++;
                } else {
                    // if the frame is not corrupt, but it is not the next frame in the sequence,
                    // send an ack for the last frame received
                    // handle the case where the first frame received is not the first frame in the sequence
                    if (seqNum != -1) {
                        ByteBuffer.wrap(ack).putShort((seqNum));
                        outStream.write(ack);
                        System.out.println(" Out of order frame " + frameSeqNum + " -- Dropping frame : Sent ACK " + seqNum);
                    } else System.out.println(" Special case!: Drop frame " + frameSeqNum);
                }
            }
            // ------------------------------------------------------------------------------------------------------
            // close the connection
            socket.close();
            inStream.close();
            outStream.close();
        } catch (Exception e) {
            e.setStackTrace(e.getStackTrace());
            System.out.println(e);
        }
        // get the bytes from the file ArrayList and write them to a file
        try {
            FileOutputStream fos = new FileOutputStream("image.png");
            for (byte[] bytes : file) {
                fos.write(bytes);
            }
            fos.close();
        } catch (Exception e) {
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