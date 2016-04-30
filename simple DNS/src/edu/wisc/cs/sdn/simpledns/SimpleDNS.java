package edu.wisc.cs.sdn.simpledns;



public class SimpleDNS 
{
	public static void main(String[] args)
	{
        String[] parsedArguments = parseArgs(args);

        DNSServer server = new DNSServer(parsedArguments[0], parsedArguments[1]);
        server.start();
	}

    /**
     * Checks for validity and parses our command line arguments
     *
     * @param args the unparsed cli arguments
     * @return an array containing the root name server in retval[0] and the amazonEC2 csv in retval[1]
     */
	private static String[] parseArgs(String[] args) {

        String[] retVals = new String[2];

        if(args.length != 4) {
            usage();
            System.exit(0);
        }
        else if(args[0].equals("-r") && args[2].equals("-e")) {
            retVals[0] = args[1];
            retVals[1] = args[3];
        }
        else if(args[0].equals("-e") && args[2].equals("-r")) {
            retVals[0] = args[3];
            retVals[1] = args[1];
        }
        else {
            usage();
            System.exit(0);
        }

        return retVals;
    }

    private static void usage() {
        System.out.println("java edu.wisc.cs.sdn.simpledns.SimpleDNS -r <root_server_ip> -e <ec2_csv>");
        System.out.println("\t-r <root_server_ip> specifies the IP address of a root DNS server");
        System.out.println("\t-e <ec2_csv> specifies the path to a .csv file that contains entries specifying the IP address ranges for each Amazon EC2 region");
    }
}