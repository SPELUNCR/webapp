package com.speluncr;

import java.net.DatagramSocket;

public class SocketShutdownHook extends Thread {
    private DatagramSocket datagramSocket;

    public SocketShutdownHook(DatagramSocket datagramSocket){
        this.datagramSocket = datagramSocket;
    }
    @Override
    public void run() {
        if (DataHandler.getInstance().isStopped()){
            return;
        }

        if (!datagramSocket.isClosed()){
            datagramSocket.close();
        }
    }
}
