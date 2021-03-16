package com.speluncr.websocket;

import javax.websocket.OnError;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
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

    @OnOpen
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
                endpt.session.getBasicRemote().sendText(String.valueOf(cps));
            }
        } catch (IOException e){
            e.printStackTrace();
        }
    }
}