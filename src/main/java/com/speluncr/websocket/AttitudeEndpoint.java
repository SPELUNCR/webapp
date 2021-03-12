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
    private Session session;
    private static final Set<AttitudeEndpoint> ENDPTS = new HashSet<>();
    private static final ArrayBlockingQueue<ByteBuffer> SEND_QUEUE = new ArrayBlockingQueue<>(3);
    private static boolean broadcasting = false;
    private static final Thread BROADCAST_THREAD = new Thread(() -> {
        broadcasting = true;
        while(broadcasting){
            try {
                // take() blocks until a message is on the queue. Then it returns the message
                ByteBuffer msg = SEND_QUEUE.take();
                for (AttitudeEndpoint endpt: ENDPTS){
                    endpt.session.getBasicRemote().sendBinary(msg);
                }
            } catch (InterruptedException | IOException e) {
                System.out.printf("Broadcast interrupted. Broadcast = %s\n",
                        broadcasting ? "true":"false");
            }
        }
    }, "Endpoint_Broadcast");

    @OnOpen
    public void onOpen(Session session){
        session.setMaxIdleTimeout(10000); // 10 second timeout (no messages in 10 s)
        this.session = session;
        if (ENDPTS.isEmpty()){
            BROADCAST_THREAD.start();
        }
        ENDPTS.add(this);
        System.out.println("Attitude Endpoint Session Opened.");
    }

    @OnClose
    public void onClose(Session session){
        ENDPTS.remove(this);

        // End runnable if there are no sockets to broadcast to
        if (ENDPTS.isEmpty()){
            SEND_QUEUE.clear();
            broadcasting = false;
            BROADCAST_THREAD.interrupt();
        }
        System.out.println("Attitude Endpoint Session Closed.");
    }

    public static void broadcast(ByteBuffer buffer){
        try {
            // drop item at head of queue if queue gets too backed up
            if (SEND_QUEUE.remainingCapacity() == 0){
                SEND_QUEUE.take(); // should not block since remaining capacity is 0
            }
            SEND_QUEUE.put(buffer);
        } catch (InterruptedException e){
            e.printStackTrace();
        }
    }
}
