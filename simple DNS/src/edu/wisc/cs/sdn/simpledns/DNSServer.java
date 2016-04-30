package edu.wisc.cs.sdn.simpledns;

import java.io.*;

import java.util.HashMap;

import java.net.ServerSocket;
import java.net.Socket;

public class DNSServer {

    private ServerSocket serverSocket;
    private Socket socket;
    private String rootServer;
    private HashMap<String, EC2Instance> ec2InstanceMap;

    DNSServer(String rootServer, String amazonEC2CSV) {
        this.rootServer = rootServer;
        this.ec2InstanceMap = parseAmazonCSV(amazonEC2CSV);
    }

    public void start() {
        try {
            this.serverSocket = new ServerSocket(8053);
            System.out.println("SimplesDNS server opened socket on port 8053...");
        }
        catch(IOException e) {
            System.out.println("SimpleDNS unable to open serverSocket on port 8053. Exiting...");
            System.exit(1);
        }

        try {
            System.out.println("SimpleDNS server now accepting DNS queries...");
            this.socket = serverSocket.accept();
        }
        catch(IOException e) {
            System.out.println("SimpleDNS unable to start accepting queries. Exiting...");
            try {
                this.serverSocket.close();
            }
            catch(IOException e2) {
                System.out.println("Unable to close serverSocket");
            }
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
