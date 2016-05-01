package edu.wisc.cs.sdn.simpledns;

import edu.wisc.cs.sdn.simpledns.packet.DNS;
import edu.wisc.cs.sdn.simpledns.packet.DNSQuestion;
import edu.wisc.cs.sdn.simpledns.packet.DNSResourceRecord;

import java.io.*;

import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

public class DNSServer {

    private DatagramSocket serverSocket;
    private InetAddress rootServer;
    private HashMap<String, EC2Instance> ec2InstanceMap;

    DNSServer(String rootServer, String amazonEC2CSV) {
        try {
            this.rootServer = InetAddress.getByName(rootServer);
        }
        catch(UnknownHostException e) {
            System.out.println("Unable to resolve ip of root name server. Exiting...");
            System.exit(1);
        }

        this.ec2InstanceMap = parseAmazonCSV(amazonEC2CSV);

        try {
            this.serverSocket = new DatagramSocket(8053);
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
                DNS dnsQuery = DNS.deserialize(packet.getData(), packet.getLength());
                if(dnsQuery.isQuery() && dnsQuery.getOpcode() == DNS.OPCODE_STANDARD_QUERY) {
                    DNS dnsResponse = handlePacket(packet, dnsQuery, this.rootServer);
                    DatagramPacket responsePacket = new DatagramPacket(dnsResponse.serialize(), dnsResponse.serialize().length, packet.getAddress(), packet.getPort());
                    serverSocket.send(responsePacket);
                }
            }
        }
        catch(IOException e) {
            System.out.println("Error while handling query packet. Exiting...");
            serverSocket.close();
            System.exit(1);
        }
    }

    private DNS handlePacket(DatagramPacket packet, DNS dnsQuery, InetAddress dnsServer) throws IOException {

        byte[] buffer = new byte[1500];
        DatagramPacket receivedPacket = new DatagramPacket(buffer, buffer.length);

        ArrayList<DNSQuestion> questions = new ArrayList<DNSQuestion>(dnsQuery.getQuestions());
        ArrayList<DNSResourceRecord> answers = new ArrayList<DNSResourceRecord>();
        ArrayList<DNSResourceRecord> authorities = new ArrayList<DNSResourceRecord>();
        ArrayList<DNSResourceRecord> additional = new ArrayList<DNSResourceRecord>();

        for (DNSQuestion question : dnsQuery.getQuestions()) {
            if (isSupportedQuestionType(question.getType())) {

                DNS dnsQuestionQuery = new DNS();
                dnsQuestionQuery.setQuestions(Collections.singletonList(question));
                dnsQuestionQuery.setTruncated(false);
                dnsQuestionQuery.setOpcode(DNS.OPCODE_STANDARD_QUERY);
                dnsQuestionQuery.setId(dnsQuery.getId());
                dnsQuestionQuery.setAuthenicated(false);
                dnsQuestionQuery.setQuery(true);
                dnsQuestionQuery.setRecursionDesired(false);

                packet.setData(dnsQuestionQuery.serialize());

                DatagramPacket queryPacket = new DatagramPacket(packet.getData(), packet.getData().length, dnsServer, 53);
                this.serverSocket.send(queryPacket);

                serverSocket.receive(receivedPacket);
                DNS queryResponse = DNS.deserialize(receivedPacket.getData(), receivedPacket.getData().length);
                answers.addAll(queryResponse.getAnswers());
                authorities.addAll(queryResponse.getAuthorities());
                additional.addAll(queryResponse.getAdditional());
            }
        }

        //we only care about further resolving the result if recursion is desired
        if(dnsQuery.isRecursionDesired() && answers.size() != 0) {
            if(answers.get(0).getType() == DNS.TYPE_NS) {

            }
            else if(answers.get(0).getType() == DNS.TYPE_CNAME) {

            }
        }

        DNS finalQueryResponse = new DNS();
        finalQueryResponse.setTruncated(false);
        finalQueryResponse.setOpcode(DNS.OPCODE_STANDARD_QUERY);
        finalQueryResponse.setId(dnsQuery.getId());
        finalQueryResponse.setAuthenicated(false);
        finalQueryResponse.setQuery(false);
        finalQueryResponse.setRecursionDesired(false);
        finalQueryResponse.setRecursionAvailable(false);
        finalQueryResponse.setQuestions(questions);
        finalQueryResponse.setAnswers(answers);
        finalQueryResponse.setAuthorities(authorities);
        finalQueryResponse.setAdditional(additional);

        return finalQueryResponse;
    }

    private boolean isSupportedQuestionType(short questionType) {
        if(questionType == DNS.TYPE_A) {
            return true;
        }
        else if(questionType == DNS.TYPE_AAAA) {
            return true;
        }
        else if(questionType == DNS.TYPE_CNAME) {
            return true;
        }
        else if(questionType == DNS.TYPE_NS) {
            return true;
        }
        else {
            return false;
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
                ec2InstanceMap.put(values[0].split("/")[0], instance);
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
