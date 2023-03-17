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

Wherever applicable, use the commands and protocol for TFTP (IETF RFC 1350), with the following modifications. You will need to design and use additional packet header information than that in TFTP; use the IETF 2347 TFTP Options Extension when possible.

Use TCP-style sliding windows rather than the sequential acks used in TFTP. Test with at least two different max window sizes.
Arrange that each session begins with a (random) number exchange to generate a key that is used for encrypting data. You can just use Xor to create key, or anything better.
Support only binary (octet) transmission.
Support a command line argument controlling whether to pretend to drop 1 percent of the packets;
 */

import java.net.*;
import java.io.*;

public class Client {

    static final int PORT = 27050;
    String host = "localhost";
    static String url = "https://www.google.com/images/branding/googlelogo/2x/googlelogo_color_272x92dp.png";
    //REACT LOGO:   https://brandslogos.com/wp-content/uploads/images/large/react-logo-1.png
    //GOOGLE LOGO:  https://www.google.com/images/branding/googlelogo/2x/googlelogo_color_272x92dp.png


    public static void main(String[] args) {
        try{
            // create a socket
            Socket socket = new Socket("localhost", PORT);
            // create in and out streams for the socket
            DataInputStream inStream = new DataInputStream(socket.getInputStream());
            DataOutputStream outStream = new DataOutputStream(socket.getOutputStream());
            // send the URL
            outStream.writeUTF(url);
            // get the file
            InputStream file = socket.getInputStream();
            // save the file
            FileOutputStream fos = new FileOutputStream("image.png");
            byte[] buffer = new byte[1024];
            int bytesRead = 0;
            while ((bytesRead = file.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
            }
            // close the connection
            socket.close();
            inStream.close();
            outStream.close();
            fos.close();
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
}