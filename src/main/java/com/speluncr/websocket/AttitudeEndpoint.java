package com.speluncr.websocket;

import javax.websocket.OnClose;
import javax.websocket.OnOpen;
import javax.websocket.RemoteEndpoint;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;

@ServerEndpoint("/attitude")
public class AttitudeEndpoint {
    private static final Set<AttitudeEndpoint> endpts = new HashSet<>();
    private RemoteEndpoint.Basic remoteEndpoint;

    @OnOpen
    private void onOpen(Session session){
        session.setMaxIdleTimeout(10000); // 10 second timeout (no messages in 10 s)
        remoteEndpoint = session.getBasicRemote();
        endpts.add(this);
    }

    @OnClose
    public void onClose(Session session){
        endpts.remove(this);
    }

    public static void broadcast(ByteBuffer buffer){
        try {
            for (AttitudeEndpoint e : endpts) {
                e.remoteEndpoint.sendBinary(buffer);
            }
        } catch (IOException e){
            e.printStackTrace();
        }
    }
}
