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
        //long time = System.nanoTime(); // get time of event
    }
}
