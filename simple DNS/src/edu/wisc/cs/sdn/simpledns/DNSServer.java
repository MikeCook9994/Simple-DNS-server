package edu.wisc.cs.sdn.simpledns;

import edu.wisc.cs.sdn.simpledns.packet.DNS;
import edu.wisc.cs.sdn.simpledns.packet.DNSQuestion;
import edu.wisc.cs.sdn.simpledns.packet.DNSRdataString;
import edu.wisc.cs.sdn.simpledns.packet.DNSResourceRecord;

import java.io.*;

import java.lang.reflect.Array;
import java.net.*;
import java.util.*;

public class DNSServer {

    private DatagramSocket serverSocket;
    private InetAddress rootServer;
    private ArrayList<EC2Instance> ec2Instances;

    DNSServer(String rootServer, String amazonEC2CSV) {
        try {
            this.rootServer = InetAddress.getByName(rootServer);
        }
        catch(UnknownHostException e) {
            System.out.println("Unable to resolve ip of root name server. Exiting...");
            System.exit(1);
        }

        this.ec2Instances = parseAmazonCSV(amazonEC2CSV);

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
                    for (DNSQuestion question : dnsQuery.getQuestions()) {
                        DNS dnsResponse = handlePacket(dnsQuery, question, this.rootServer);
                        if(dnsResponse != null && !dnsResponse.getAnswers().isEmpty() && dnsResponse.getAnswers().get(0).getType() == question.getType()) {
                            System.out.println(dnsResponse.toString());
                            if(question.getType() == DNS.TYPE_A) {
                                dnsResponse.getAnswers().addAll(getEC2TXTRecords(dnsResponse.getAnswers()));
                            }
                            DatagramPacket responsePacket = new DatagramPacket(dnsResponse.serialize(), dnsResponse.serialize().length, packet.getAddress(), packet.getPort());
                            serverSocket.send(responsePacket);
                        }
                    }

                }
            }
        }
        catch(IOException e) {
            System.out.println("Error while handling query packet. Exiting...");
            serverSocket.close();
            System.exit(1);
        }
    }

    private DNS handlePacket(DNS dnsQuery, DNSQuestion question, InetAddress dnsServer) throws IOException {

        byte[] buffer = new byte[1500];
        DatagramPacket receivedPacket = new DatagramPacket(buffer, buffer.length);

        if (isSupportedQuestionType(question.getType())) {

            byte[] dnsQuestionQuery = constructDNSQuery(dnsQuery.getId(), question).serialize();

            DatagramPacket queryPacket = new DatagramPacket(dnsQuestionQuery, dnsQuestionQuery.length, dnsServer, 53);
            this.serverSocket.send(queryPacket);

            serverSocket.receive(receivedPacket);
            DNS queryResponse = DNS.deserialize(receivedPacket.getData(), receivedPacket.getData().length);
            System.out.println(queryResponse.toString());

            ArrayList<DNSResourceRecord> answers = new ArrayList<>(queryResponse.getAnswers());
            ArrayList<DNSResourceRecord> authorities = new ArrayList<>(queryResponse.getAuthorities());
            ArrayList<DNSResourceRecord> additionals = new ArrayList<>(queryResponse.getAdditional());

            //we only care about further resolving the result if recursion is desired
            if(dnsQuery.isRecursionDesired()) {
                //if we did not find an answer, we want to use our additional/authority section to recurse
                if(!isQueryResolved(answers, question)) {
                    DNS recursiveReply = queryResponse;
                    if(!authorities.isEmpty() && authorities.get(0).getType() == DNS.TYPE_NS) {
                        InetAddress nextDNS = resolveNextServer(authorities, additionals);
                        if(nextDNS == null) {
                            return null;
                        }
                        recursiveReply = handlePacket(dnsQuery, question, nextDNS);
                    }
                    else if(!authorities.isEmpty() && authorities.get(0).getType() == DNS.TYPE_CNAME) {
                        recursiveReply = handlePacket(dnsQuery, rewriteQuestion(answers.get(0), question), this.rootServer);
                    }
                    if(recursiveReply == null) {
                        return null;
                    }
                    answers = new ArrayList<>(recursiveReply.getAnswers());
                    authorities = new ArrayList<>(recursiveReply.getAuthorities());
                    additionals = new ArrayList<>(recursiveReply.getAdditional());
                }
            }
            return constructDNSReply(dnsQuery.getId(), question, answers, authorities, additionals);
        }
        return null;
    }

    private DNSQuestion rewriteQuestion(DNSResourceRecord answer, DNSQuestion question) {
        DNSQuestion rewrittenQuestion = question;
        rewrittenQuestion.setName(answer.getData().toString());
        return rewrittenQuestion;
    }

    private InetAddress resolveNextServer(ArrayList<DNSResourceRecord> authorities, ArrayList<DNSResourceRecord> additionals) {
        for(DNSResourceRecord authority : authorities) {
            if(authority.getType() == DNS.TYPE_NS) {
                for(DNSResourceRecord additional : additionals) {
                    if(authority.getData().toString().equals(additional.getName()) && additional.getType() != DNS.TYPE_AAAA) {
                        try{
                            return InetAddress.getByName(additional.getData().toString());
                        }
                        catch(UnknownHostException e) {
                            System.out.println("Unable to resolve ip address of next name server. Exiting...");
                            System.exit(1);
                        }
                    }
                }
            }
        }

        return null;
    }

    private DNS constructDNSQuery(short id, DNSQuestion question) {

        DNS dnsQuery = new DNS();

        dnsQuery.setId(id);

        dnsQuery.setOpcode(DNS.OPCODE_STANDARD_QUERY);

        dnsQuery.setTruncated(false);
        dnsQuery.setAuthenicated(false);
        dnsQuery.setQuery(true);
        dnsQuery.setRecursionDesired(false);

        dnsQuery.setQuestions(Collections.singletonList(question));

        return dnsQuery;
    }

    private DNS constructDNSReply(short id, DNSQuestion question, ArrayList<DNSResourceRecord> answers, ArrayList<DNSResourceRecord> authorities, ArrayList<DNSResourceRecord> additionals) {

        DNS finalQueryResponse = new DNS();

        finalQueryResponse.setId(id);
        finalQueryResponse.setOpcode(DNS.OPCODE_STANDARD_QUERY);

        finalQueryResponse.setTruncated(false);
        finalQueryResponse.setAuthenicated(false);
        finalQueryResponse.setQuery(false);
        finalQueryResponse.setRecursionDesired(false);
        finalQueryResponse.setRecursionAvailable(false);

        finalQueryResponse.setQuestions(Collections.singletonList(question));
        finalQueryResponse.setAnswers(answers);
        finalQueryResponse.setAuthorities(authorities);
        finalQueryResponse.setAdditional(additionals);

        return finalQueryResponse;
    }

    private boolean isQueryResolved(ArrayList<DNSResourceRecord> answers, DNSQuestion question) {
        for(DNSResourceRecord answer : answers) {
            if(answer.getType() == question.getType() && answer.getName().equals(question.getName())) {
                return true;
            }
        }
        return false;
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

    private ArrayList<EC2Instance> parseAmazonCSV(String amazonEC2CSV) {

        ArrayList<EC2Instance> ec2Instances = new ArrayList<>();

        try {
            FileReader reader = new FileReader(amazonEC2CSV);
            BufferedReader bufferedReader = new BufferedReader(reader);

            String line;
            while((line = bufferedReader.readLine()) != null) {
                String[] values = line.split(",");
                EC2Instance instance = new EC2Instance(values[0], values[1]);
                ec2Instances.add(instance);
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

        return ec2Instances;
    }

    private ArrayList<DNSResourceRecord> getEC2TXTRecords(List<DNSResourceRecord> answers) {

        ArrayList<DNSResourceRecord> txtRecords = new ArrayList<>();

        for(DNSResourceRecord answer : answers) {
            if(answer.getType() == DNS.TYPE_A) {

                EC2Instance matchingInstance = isInEC2Region(answer.getData().toString());
                if(matchingInstance != null) {
                    txtRecords.add(constructEC2TXTRecord(matchingInstance, answer));
                }
            }
        }

        return txtRecords;
    }

    private EC2Instance isInEC2Region(String ip) {

        int answerIpAsInt = stringIPtoInt(ip);

        for(EC2Instance instance : this.ec2Instances) {

            String[] instanceIPAndPrefix = instance.getIp().split("/");
            int maskShift = Integer.parseInt(instanceIPAndPrefix[1]);
            int EC2InstanceIp = stringIPtoInt(instanceIPAndPrefix[0]);

            if (EC2InstanceIp == (answerIpAsInt & (-1 << maskShift))) {
                return instance;
            }
        }
        return null;
    }

    private int stringIPtoInt(String ip) {
        int[] ipParts = new int[4];
        String[] parts = ip.split("\\.");

        for (int i = 0; i < 4; i++) {
            ipParts[i] = Integer.parseInt(parts[i]);
        }

        int ipNumbers = 0;
        for (int i = 0; i < 4; i++) {
            ipNumbers += ipParts[i] << (24 - (8 * i));
        }
        return ipNumbers;
    }

    private DNSResourceRecord constructEC2TXTRecord(EC2Instance instance, DNSResourceRecord answer) {
        return new DNSResourceRecord(answer.getName(), (short)16, new DNSRdataString(instance.getLocation()+ "-" + instance.getIp().split("/")[0]));
    }
}