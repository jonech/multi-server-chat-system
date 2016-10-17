package au.edu.unimelb.comp90015_chjq.authserver;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

public class Main {

    public static void main(String[] args) {
        //Object that will store the parsed command line arguments
        CmdArgs argsBean = new CmdArgs();

        //Parser provided by args4j
        CmdLineParser parser = new CmdLineParser(argsBean);
        try {
            //Parse the arguments
            parser.parseArgument(args);
            //Do the initialization--pay attention to order;
            //Specify the keystore details (this can be specified as VM arguments as well)
            //the keystore file contains an application's own certificate and private key
            //keytool -genkey -keystore <keystorename> -keyalg RSA
            System.setProperty("javax.net.ssl.keyStore","chjq-keystore");
            //Password to access the private key from the keystore file
            System.setProperty("javax.net.ssl.keyStorePassword", "123456");

            // Enable debugging to view the handshake and communication which happens between the SSLClient and the SSLServer
            //System.setProperty("javax.net.debug","all");
            AuthServer authServer = AuthServer.init(argsBean.getServerid(), argsBean.getServers_conf());
            authServer.run();



        }
        catch (CmdLineException e) {

            System.err.println(e.getMessage());

            //Print the usage to help the user understand the arguments expected
            //by the program
            parser.printUsage(System.err);
        }
    }
}
