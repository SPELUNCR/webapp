package com.speluncr.websocket;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashSet;
import java.util.Set;

@ServerEndpoint("/radiation")
public class RadiationEndpoint {
    private static final Set<RadiationEndpoint> ENDPTS = new HashSet<>();
    private Session session;

    @OnOpen
    public void onOpen(Session session){
        this.session = session;
        ENDPTS.add(this);
    }

    @OnClose
    public void onClose(Session session){
        ENDPTS.remove(this);
        System.out.printf("Radiation Endpoint Session %s Closed.\n", session.getId());
    }

    @OnError
    public void onError(Session session, Throwable throwable){
        ENDPTS.remove(this);
        System.err.printf("Radiation Endpoint Session %s Error: %s\n", session.getId(), throwable);
        try {
            session.close();
        } catch (IOException e){
            System.err.printf("onError(): Failed to close session %s after error occurred.", session.getId());
            e.printStackTrace();
        }
    }

    public static synchronized void broadcast(int cps){
        try {
            for (RadiationEndpoint endpt : ENDPTS) {
                ByteBuffer payload = ByteBuffer.allocate(Integer.BYTES);
                payload.order(ByteOrder.LITTLE_ENDIAN);
                payload.putInt(cps);
                payload.position(0);
                endpt.session.getBasicRemote().sendBinary(payload);
            }
        } catch (IOException e){
            e.printStackTrace();
        }
    }
}