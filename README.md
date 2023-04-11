# CSC445_A2

Assignment 2
Write a proxy server program, that relays files/pages. To demonstrate, you'll need a client and a server program:
The proxy server awaits connections.
A client connects, sends a URL.
The proxy server gets the corresponding page/file using HTTP. The proxy server caches (immediately sending instead of fetching) at least the most recent request; optionally more.
The proxy sends the page/file to the client, that then displays it. For purposes of this assignment, it is OK if the only kinds of files displayed are images (for example jpg).
Wherever applicable, use the commands and protocol for TFTP (IETF RFC 1350), with the following modifications. You will need to design and use additional packet header information than that in TFTP; use the IETF 2347 TFTP Options Extension when possible.

Use TCP-style sliding windows rather than the sequential acks used in TFTP. Test with at least two different max window sizes.
Arrange that each session begins with a (random) number exchange to generate a key that is used for encrypting data. You can just use Xor to create key, or anything better.
Support only binary (octet) transmission.
Support a command line argument controlling whether to pretend to drop 1 percent of the packets;
Create a web page showing throughput across varying conditions: at least 2 different clients hosts, different window sizes; drops vs no drops.
