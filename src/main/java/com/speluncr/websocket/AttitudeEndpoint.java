package com.speluncr.websocket;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;

@ServerEndpoint("/attitude")
public class AttitudeEndpoint {
    private static final Set<AttitudeEndpoint> endpts = new HashSet<>();
    private final ArrayBlockingQueue<ByteBuffer> sendQueue = new ArrayBlockingQueue<>(3);
    private boolean broadcasting = false;
    private Session session;
    private final Thread BROADCAST_THREAD = new Thread(new Runnable() {
        @Override
        public void run() {
            broadcasting = true;
            while(broadcasting){
                try {
                    // take() blocks until a message is on the queue. Then it returns the message
                    ByteBuffer msg = sendQueue.take();
                    session.getBasicRemote().sendBinary(msg);
                } catch (InterruptedException | IOException e) {
                    System.out.printf("Broadcast interrupted. Broadcast = %s\n",
                            broadcasting ? "true":"false");
                }
            }
        }
    }, "Endpoint_Broadcast");

    @OnOpen
    public void onOpen(Session session){
        if (endpts.isEmpty()){
            BROADCAST_THREAD.start();
        }
        session.setMaxIdleTimeout(10000); // 10 second timeout (no messages in 10 s)
        this.session = session;
        endpts.add(this);
        System.out.println("Attitude Endpoint Opened");
    }

    @OnClose
    public void onClose(){
        this.sendQueue.clear();
        endpts.remove(this);

        // End runnable if there are no sockets to broadcast to
        if (endpts.isEmpty()){
            broadcasting = false;
            BROADCAST_THREAD.interrupt();
        }
    }

    public static void broadcast(ByteBuffer buffer){
        for (AttitudeEndpoint endpt : endpts) {
            try {
                // drop item at head of queue if queue gets too backed up
                if (endpt.sendQueue.remainingCapacity() == 0){
                    endpt.sendQueue.take(); // should not block since remaining capacity is 0
                }
                endpt.sendQueue.put(buffer);
            } catch (InterruptedException e){
                e.printStackTrace();
            }
        }
    }
}
