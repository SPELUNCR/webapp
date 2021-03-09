package com.speluncr.gpio;

import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import com.speluncr.TelemetryServlet;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/************************************************************
 * This class will initialize the GPIO pin for the geiger
 * counter, listen for activity, and allow other classes to
 * access data collected from the geiger counter.
 ************************************************************/
public class RadiationSensor implements Sensor {
    private final TelemetryServlet servlet;
    private DataOutputStream outputStream;
    private long initTime = 0;
    private int[] cps = new int[60]; // store the counts of the last 60 1-second intervals
    private int cpsIdx = 0; // index of current 1 second interval

    public RadiationSensor(TelemetryServlet telemetryServlet){
        servlet = telemetryServlet;
    }

    public void initializeSensor(){
        System.out.println("Initializing Geiger Counter...");

        GpioPinDigitalInput inpin = GPIOInitializer.getInstance().getGpioController()
                .provisionDigitalInputPin(RaspiPin.GPIO_07, PinPullResistance.PULL_UP);
        inpin.addListener((GpioPinListenerDigital) event -> {
            if (event.getState().isLow()){
                count();
            }
        });

        try {
            outputStream = new DataOutputStream(new FileOutputStream(
                    servlet.getProperties().getProperty("RadiationDataFile")));
        } catch (IOException e) {
            e.printStackTrace();
        }

        initTime = System.nanoTime();
    }

    public void stopSensor(){
        System.out.println("Stopping Geiger Counter...");
        // Save and close data file if open
        if (outputStream != null){
            try {
            outputStream.flush();
            outputStream.close();
            } catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    private void count(){
        long time = System.nanoTime(); // get time of event
        try {
            outputStream.writeLong(time);
        } catch (IOException e){
            System.err.printf("count(): Failed to write value %d to data file\n", time);
            e.printStackTrace();
        }


    }
}
