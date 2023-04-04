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
    static String HOST = "cs.oswego.edu";
    /*REACT LOGO:*/   //static String url = "https://brandslogos.com/wp-content/uploads/images/large/react-logo-1.png";
    /*GOOGLE LOGO:*/  static String url = "https://www.google.com/images/branding/googlelogo/2x/googlelogo_color_272x92dp.png";
    static String fileName = url.substring(url.lastIndexOf('/') + 1);
    /*
    ---------------------------------------------
    |   seqNum  |      data      |   checksum   |
    |  2 Bytes  |    512 Bytes   |   2 Bytes    |
    ---------------------------------------------
    */
    static final int SEQ_SIZE = 2;
    static final int DATA_SIZE = 1024;
    static final int CHECKSUM_SIZE = 2;
    static final boolean DROP_PACKETS = false;
    static final int DROP_PERCENT = 1;


    public static void main(String[] args) {
        ArrayList<byte[]> fileBytes = new ArrayList<>();
        //-------------------------------------------------------------------------------------------------------
        // setup the socket and streams, send the key and URL |
        //-----------------------------------------------------
        try {
            System.out.println("Client started");
            // create a socket
            Socket socket = new Socket(HOST, PORT);
            // create in and out streams for the socket
            DataInputStream inStream = new DataInputStream(socket.getInputStream());
            DataOutputStream outStream = new DataOutputStream(socket.getOutputStream());
            // create and send a random in bounded by the max value of a byte [-128, 127]
            int key = new Random().nextInt(Byte.MAX_VALUE);
            System.out.println("Sending key [" + key + "]");
            outStream.writeInt(key);
            // send the URL
            outStream.writeUTF(url);
            //listen for the number of expected frames and ACK it back
            int numFrames = inStream.readInt();
            System.out.println("Expecting [" + numFrames + "] frames...");
            outStream.writeInt(numFrames);
            //-------------------------------------------------------------------------------------------------------
            //  get the file via sliding window go-back-n protocol |
            //------------------------------------------------------
            byte[] frame = new byte[SEQ_SIZE + DATA_SIZE + CHECKSUM_SIZE];
            byte[] data = new byte[DATA_SIZE];
            byte[] seqNum = new byte[SEQ_SIZE];
            byte[] ack = new byte[SEQ_SIZE]; // the ack to send
            byte[] checksum = new byte[CHECKSUM_SIZE]; // the checksum of the data
            short nextSeqNum = 0; // the sequence number of the next frame expected
            int sleepyCount = 0;
            Random rand = new Random();
            long startTime = System.nanoTime();
            while (!socket.isClosed() && fileBytes.size() < numFrames){
                // listen for the next frame
                sleepyCount++;
                Thread.sleep(1);
                inStream.read(frame);
                System.arraycopy(frame, 0, seqNum, 0, SEQ_SIZE);
                System.arraycopy(frame, SEQ_SIZE, data, 0, DATA_SIZE);
                System.arraycopy(frame, SEQ_SIZE+DATA_SIZE, checksum, 0, CHECKSUM_SIZE);
                System.out.println("Received frame [" + ByteBuffer.wrap(seqNum).getShort() + "]");
                // frame is expected this and isn't corrupt
                short frameSeqNum = ByteBuffer.wrap(seqNum).getShort();
                short frameChecksum = ByteBuffer.wrap(checksum).getShort();
                if(!isCorrupt(data, frameChecksum) && frameSeqNum == nextSeqNum){
                    System.out.println("Looks good!");
                    //deliver data to fileBytes
                    System.arraycopy(frame, SEQ_SIZE, data, 0, DATA_SIZE);
                    fileBytes.add(data);
                    data = new byte[DATA_SIZE];
                    //send ACK of the next sequence number
                    ack = ByteBuffer.allocate(SEQ_SIZE).putShort(nextSeqNum).array();
                    if (DROP_PACKETS && rand.nextInt(99) < DROP_PERCENT) {
                        System.out.println("[X] Dropping ACK [" + ByteBuffer.wrap(ack).getShort() + "]");
                    } else {
                        outStream.write(ack);
                        System.out.println("Sent ACK [" + ByteBuffer.wrap(ack).getShort() + "]");
                    }
                    //increment expected sequence number
                    nextSeqNum++;
                } else {
                    // Drop frame
                    System.out.println("[X] Corrupt or out of order frame! Dropping...");
                    // send ACK of the next sequence number
                    if (nextSeqNum == 0) {
                        ack = ByteBuffer.allocate(SEQ_SIZE).putShort((short) 0).array();
                    } else {
                        ack = ByteBuffer.allocate(SEQ_SIZE).putShort((short) (nextSeqNum - 1)).array();
                    }
                    if (DROP_PACKETS && rand.nextInt(99) < DROP_PERCENT) {
                        System.out.println("[X] Dropping ACK [" + ByteBuffer.wrap(ack).getShort() + "]");
                    } else {
                        outStream.write(ack);
                        System.out.println("Sent ACK [" + ByteBuffer.wrap(ack).getShort() + "]");
                    }
                }
            }
            //-------------------------------------------------------------------------------------------------------
            //  calculations, decryption  |
            //-----------------------------
            long endTime = System.nanoTime();
            double duration = (double) (endTime - startTime) / 1_000_000_000; // in seconds
            System.out.println("Sleepy count: " + sleepyCount);
            System.out.println("File of size " + (fileBytes.size()*DATA_SIZE) + " bytes " + " received in " + duration + " seconds");
            // Throughput in Mb/s
            System.out.println("Throughput: " + (((fileBytes.size()*DATA_SIZE) * 8) / (duration))/1_000_000 + " Mb/s");

            //decrypt the file
            for (byte[] bytes : fileBytes) {
                for (int i = 0; i < bytes.length; i++) {
                    bytes[i] = (byte) (bytes[i] ^ key);
                }
            }
            //-------------------------------------------------------------------------------------------------------
            //open the image in the default image viewer -- Windows only |
            //------------------------------------------------------------
            /*try {
                Runtime.getRuntime().exec("rundll32 url.dll,FileProtocolHandler " + fileName);
            } catch (Exception e) {
                e.setStackTrace(e.getStackTrace());
                System.out.println(e);
            }*/

            // close the connection
            socket.close();
            inStream.close();
            outStream.close();
        } catch (Exception e) {
            e.setStackTrace(e.getStackTrace());
            System.out.println(e);
        }
        //-------------------------------------------------------------------------------------------------------
        // write the file to disk |
        //-------------------------
        try {
            FileOutputStream fos = new FileOutputStream(fileName);
            for (byte[] bytes : fileBytes) {
                fos.write(bytes);
            }
            fos.close();
        } catch (Exception e) {
            e.setStackTrace(e.getStackTrace());
            System.out.println(e);
        }
    }

    ///----------------///
    /// HELPER METHODS ///
    ///----------------///

    /*private static byte[] xor(byte[] msg, int key) {
        byte[] xorMsg = new byte[msg.length];
        for (int i = 0; i < msg.length; i++) {
            xorMsg[i] = (byte) (msg[i] ^ key);
        }
        return xorMsg;
    }*/
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