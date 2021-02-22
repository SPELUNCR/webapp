package com.speluncr;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**************************************************************************************
 *  The following class defines a server-side websocket endpoint for the data.html page
 *  The onOpen, onMessage, onClose and onError methods are event handlers for websocket
 *  events. The sendData() and broadcast() methods are used to send binary data to
 *  websocket endpoints
 *
 *  The Tomcat Websocket 9.0.33 dependencies were from the Maven Repository (link-below):
 *  https://mvnrepository.com/artifact/org.apache.tomcat/tomcat-websocket

 *  The Tomcat Websocket API documentation can be viewed at the url below:
 *  https://tomcat.apache.org/tomcat-9.0-doc/websocketapi/index.html
 *  Other online documentation for javax.websocket packages can be searched online
 *************************************************************************************/
@ServerEndpoint("/data")
public class DataSocketEndpoint {
    private static Set<DataSocketEndpoint> dataPageEndpoints = new CopyOnWriteArraySet<DataSocketEndpoint>(); // Need to know all endpoints for broadcasting data
    private Session session; // Two-way channel between two endpoints

    @OnOpen //
    public void onOpen(Session session) throws IOException{
        // Keep track of sessions and endpoints and start the DataHandler if needed
        this.session = session;
        dataPageEndpoints.add(this);
        if (DataHandler.getInstance().isStopped()){
            DataHandler.getInstance().start();
        }
        System.out.println("Opened session " + session.getId());
    }

    @OnMessage // Event handler for endpoint receiving data
    public void onMessage(Session session, String message) throws IOException{
        //Ignore client messages for now
        System.out.println("Client Sent: " + message);
    }

    @OnClose // Event handler for endpoint close event
    public void onClose(Session session) throws IOException {
        dataPageEndpoints.remove(this);
        System.out.println("Session " + session.getId() + " closed");
    }

    @OnError // Event handler for websocket error
    public void onError(Session session, Throwable throwable) {
        throwable.printStackTrace();
    }

    // Send a byte array to this instance of Session
    private void sendData(byte[] data){
        session.getAsyncRemote().sendBinary(ByteBuffer.wrap(data));
    }

    // Send a byte array to all websockets
    public synchronized static void broadcast(byte[] data){
        for (DataSocketEndpoint s: dataPageEndpoints){
            s.sendData(data);
        }
    }
}
