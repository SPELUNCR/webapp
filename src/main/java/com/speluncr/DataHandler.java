package com.speluncr;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;

/**************************************************************************************
 * DataHandler has only one instance (singleton) that is created when getInstance() is
 * called by the first websocket client that connects to the server. The DataHandler
 * listens for UDP packets arriving at PORT and directs them to the appropriate web sockets
 **************************************************************************************/
public class DataHandler {
    private static final DataHandler instance = new DataHandler(); //Singleton instance
    private StringBuffer postData = new StringBuffer(1024);
    private boolean stopped = true; // Indicates if DataHandler is listening and repeating data
    private Properties properties = new Properties();

    // Private class constructor for singleton
    private DataHandler(){
        File propertiesFile = null;

        // Navigate to the servlet.conf file that contains properties used by this class
        // The following try-catch block is only necessary because the absolute path
        // of servlet.conf will be different on other computers and I want this to work
        // right away so you don't have to hard-code your specific path and recompile this code.
        try {
            Path classPath = Path.of(DataHandler.class.getResource("/com/speluncr/DataHandler.class").toURI());
            String targetString = System.getProperty("file.separator") +
                    classPath.subpath(0,classPath.getNameCount()-4).resolve("servlet.conf").toString();

            propertiesFile = new File(targetString);
            System.out.format("Attempting to load properties from: %s%n", propertiesFile.getAbsolutePath());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        // Stop if there's no file to load the properties
        if (propertiesFile == null || !propertiesFile.exists()){
            System.err.println("[ERROR]: The properties file could not be read");
            return;
        }

        // Load properties from file
        try (BufferedReader br = new BufferedReader(new FileReader(propertiesFile))){
            properties.load(br);
            System.out.println("[SUCCESS]: Properties loaded from file");
        } catch (IOException e){
            e.printStackTrace();
        }

        System.out.println("Datahandler constructed");
    }

    // Lets other classes in this package check the run state of the DataHandler
    synchronized boolean isStopped(){
        return stopped;
    }

    //Listens to incoming data from a socket and broadcasts to the appropriate websockets
    void start(){
        // Do not start if it is already running
        if (!stopped){
            System.out.println("DataHandler is already running");
            return;
        }

        beginNewPOST(); // Start the POST report with whatever is already in the file (if anything)

        // Defines the work for a new thread that will listen for incoming data on a socket and
        // respond to that date (usually by broadcasting to the appropriate websockets)
        Runnable runnable = () -> {
            final int PORT         = Integer.parseInt(properties.getProperty("port","1701"));       // Incoming data port on the localhost
            final int PAYLOAD_SIZE = Integer.parseInt(properties.getProperty("payloadSize","1024"));// Number of bytes the datagramSocket can receive
            final short EXIT_CODE  = Short.parseShort(properties.getProperty("exitCode","-1"));     // Breaks the loop and ends this thread
            final short POST_CODE  = Short.parseShort(properties.getProperty("postCode","0"));      // Indicates data that needs to go to the POSTSocketEndpoints
            final short DATA_CODE  = Short.parseShort(properties.getProperty("dataCode","1"));      // Indicates data that needs to go to the DataSocketEndpoints


            System.out.format("PORT            = %d%n" +
                            "PAYLOAD_SIZE    = %d%n" +
                            "EXIT_CODE       = %d%n" +
                            "POST_CODE       = %d%n" +
                            "DATA_CODE       = %d%n",
                    PORT, PAYLOAD_SIZE, EXIT_CODE, POST_CODE, DATA_CODE);

            // Create the UDP socket and then handle incoming data until loop is broken by EXIT_CODE or interrupt
            try (DatagramSocket datagramSocket = new DatagramSocket(PORT)) {
                DatagramPacket packet = new DatagramPacket(new byte[PAYLOAD_SIZE], PAYLOAD_SIZE);
                Runtime.getRuntime().addShutdownHook(new SocketShutdownHook(datagramSocket));
                short code, length; // Packet should begin with these two shorts
                byte[] data;

                do {
                    // receive() blocks until a packet is received or the timeout throws the SocketTimeoutException
                    datagramSocket.receive(packet);
                    data = packet.getData();

                    // Data byte order should be little endian
                    code = (short) (((data[1] & 0xff) << 8) | (data[0] & 0xff));
                    length = (short) (((data[3] & 0xff) << 8) | (data[2] & 0xff));

                    // Determine which websocket endpoints to broadcast to or shut down this DataHandler thread
                    if (code == DATA_CODE) {
                        DataSocketEndpoint.broadcast(Arrays.copyOfRange(data, 4, length + 4));
                    } else if (code == POST_CODE) {
                        String message = new String(Arrays.copyOfRange(packet.getData(), 4, PAYLOAD_SIZE)).trim() + "<br>";
                        postData.append(message);
                    } else if (code == EXIT_CODE) {
                        break;
                    }
                    packet.setData(new byte[PAYLOAD_SIZE]);
                } while (true);
            } catch (SocketException e){
                System.err.println(e.getMessage());
            } catch (IOException e){
                e.printStackTrace();
            }
            // At this point, the run method is about to finish after which, the thread will end.
            stopped = true;
            System.out.println("DataHandler stopped");
        };
        // Create the thread from the runnable defined above
        Thread runningDataHandler = new Thread(runnable, "DataHandler");
        runningDataHandler.setDaemon(true); // Thread must end when all non-daemon threads have ended
        runningDataHandler.start();
        stopped = false;
        System.out.println("DataHandler started");
    }

    // Allows other classes in this package to access the DataHandler singleton
    static DataHandler getInstance(){
        return instance;
    }

    // Called when POSTSocketEndpoints open so client gets current POST data
    String getPostData(){
        return postData.toString();
    }

    // Ensures that existing postData is cleared. Then uses data from POST file if there is any.
    private void beginNewPOST(){
        String POSTString = properties.getProperty("postFile","/home/paul/Documents/Software_Development/Gepeto/webapps/ROOT/POST_ReadMe.txt");
        final File POSTFile = new File(POSTString);
        postData.delete(0,postData.length());
        postData.append("-----Begin POST Results-----<br>");
    }
}
