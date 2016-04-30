package edu.wisc.cs.sdn.simpledns;

import edu.wisc.cs.sdn.simpledns.packet.DNS;

import java.io.*;

import java.net.*;
import java.util.HashMap;

public class DNSServer {

    private DatagramSocket serverSocket;
    private String rootServer;
    private HashMap<String, EC2Instance> ec2InstanceMap;

    DNSServer(String rootServer, String amazonEC2CSV) {
        this.rootServer = rootServer;
        this.ec2InstanceMap = parseAmazonCSV(amazonEC2CSV);
        try {
            this.serverSocket = new DatagramSocket(8053);
            System.out.println("SimplesDNS server opened socket on port 8053...");
        }
        catch(SocketException e) {
            System.out.println("SimpleDNS unable to open serverSocket on port 8053. Exiting...");
            System.exit(1);
        }
    }

    public void run() {
        try {
            while(true) {
                byte[] buffer = new byte[1500];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                serverSocket.receive(packet);
                DNS dnsPacket = DNS.deserialize(packet.getData(), packet.getLength());
                System.out.println(dnsPacket.toString());
            }
        }
        catch(IOException e) {
            System.out.println("Error while receiving query packet. Exiting...");
            serverSocket.close();
            System.exit(1);
        }
    }

    private void respond(DatagramPacket packet) {
        try {
            serverSocket.send(packet);
        }
        catch(IOException e) {
            System.out.println("Error while sending response packet. Exiting...");
            serverSocket.close();
            System.exit(1);
        }
    }

    private HashMap<String, EC2Instance> parseAmazonCSV(String amazonEC2CSV) {

        HashMap<String, EC2Instance> ec2InstanceMap = new HashMap<String, EC2Instance>();

        try {
            FileReader reader = new FileReader(amazonEC2CSV);
            BufferedReader bufferedReader = new BufferedReader(reader);

            String line;
            while((line = bufferedReader.readLine()) != null) {
                String[] values = line.split(",");
                EC2Instance instance = new EC2Instance(values[0], values[1]);
                ec2InstanceMap.put(values[0], instance);
            }
        }
        catch(FileNotFoundException e) {
            System.out.println("Path of provided .csv file does not exist. Exiting...");
            System.exit(1);
        }
        catch(IOException e) {
            System.out.println("Error while reading file. Exiting...");
            System.exit(1);
        }

        return ec2InstanceMap;
    }

}
