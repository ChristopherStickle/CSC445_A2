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

Wherever applicable, use the commands and protocol for TFTP (IETF RFC 1350), with the following modifications. You will need to design and use additional packet header information than that in TFTP; use the IETF 2347 TFTP Options Extension when possible.

Use TCP-style sliding windows rather than the sequential acks used in TFTP. Test with at least two different max window sizes.
Arrange that each session begins with a (random) number exchange to generate a key that is used for encrypting data. You can just use Xor to create key, or anything better.
Support only binary (octet) transmission.
Support a command line argument controlling whether to pretend to drop 1 percent of the packets;
 */
import java.net.*;
import java.io.*;
public class Sever {
    static final int PORT = 27050;
    public static void main(String[] args) {
        try {
            ServerSocket serverSocket = new ServerSocket(PORT);
            while (true) {
                // Wait for a connection
                Socket client = serverSocket.accept();
                System.out.println("Connection from " + client.getInetAddress());
                // create in and out streams for the socket
                DataInputStream inStream = new DataInputStream(client.getInputStream());
                DataOutputStream outStream = new DataOutputStream(client.getOutputStream());
                // get the URL
                String url = inStream.readUTF();
                //The proxy server gets the corresponding page/file using HTTP.
                URL urlObj = new URL(url);
                HttpURLConnection connection = (HttpURLConnection) urlObj.openConnection();
                connection.setRequestMethod("GET");
                connection.connect();
                // get the file/page
                InputStream file = connection.getInputStream();
                // send the file


                byte[] buffer = new byte[1024];
                int bytesRead = 0;
                while ((bytesRead = file.read(buffer)) != -1) {
                    outStream.write(buffer, 0, bytesRead);
                }


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

}
