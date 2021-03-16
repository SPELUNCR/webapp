package com.speluncr.gpio;

import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import com.speluncr.TelemetryServlet;
import com.speluncr.websocket.RadiationEndpoint;
import java.io.*;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.Timer;
import java.util.TimerTask;

/************************************************************
 * This class will initialize the GPIO pin for the geiger
 * counter, listen for activity, and allow other classes to
 * access data collected from the geiger counter.
 ************************************************************/
public class RadiationSensor implements Sensor {
    private final Timer COUNT_TIMER = new Timer();
    private final TelemetryServlet servlet;
    private DataOutputStream outputStream = null;
    private GpioPinDigitalInput inpin = null;
    private long initTime = 0; // time when sensor started
    private int cps = 0; // counts in the current 1-second period

    public RadiationSensor(TelemetryServlet telemetryServlet){
        servlet = telemetryServlet;
    }

    public void startSensor(){
        System.out.println("Initializing Geiger Counter...");

        // Open a data file for the raw data
        String fileName = "radiation-" + LocalDateTime.now().toString() + ".data";
        File saveFile = new File(servlet.getProperties().getProperty(
                "RadiationSaveDirectory", System.getProperty("user.home")), fileName);
        try {
            // Attempt to create output stream file in directory specified in servlet.conf
            outputStream = new DataOutputStream(new FileOutputStream(saveFile));
        }catch (FileNotFoundException e){
            // Attempt to put file in user home directory if directory from properties file does not work
            // Only try user.home if it hasn't already been tried.
            if (!saveFile.toPath().getParent().equals(Path.of(System.getProperty("user.home")))){
                System.out.printf("[INFO]: Output file at %s could not be created.\n", saveFile.getPath());
                saveFile = new File(System.getProperty("user.home"), fileName);
                try {
                    outputStream = new DataOutputStream(new FileOutputStream(saveFile));
                    System.out.printf("[INFO]: Using output file at %s.\n", saveFile.getPath());
                } catch (FileNotFoundException fileNotFoundException) {
                    System.err.printf("[ERROR]: Failed to create radiation output file at %s.\n", saveFile.getPath());
                    fileNotFoundException.printStackTrace();
                }
            } else {
                System.err.printf("[ERROR]: Failed to create radiation output file at %s.\n", saveFile.getPath());
                e.printStackTrace();
            }
        }

        // Set pin 7 as digital input
        inpin = GPIOInitializer.getInstance().getGpioController()
                .provisionDigitalInputPin(RaspiPin.GPIO_07, PinPullResistance.PULL_UP);

        // Set initial time
        initTime = System.nanoTime();

        // Add listener to pin 7 to handle when signal goes low (count occurs)
        inpin.addListener((GpioPinListenerDigital) event -> {
            if (event.getState().isLow()){
                long time = System.nanoTime() - initTime; // get time of event relative to initTime
                cps++;

                // Write raw interrupt time to data file
                try {
                    outputStream.writeLong(time);
                } catch (IOException e){
                    System.err.printf("count(): Failed to write value %d to data file\n", time);
                    e.printStackTrace();
                }
            }
        });

        COUNT_TIMER.schedule(new TimerTask() {
            @Override
            public void run() {
                RadiationEndpoint.broadcast(cps); // send the number of counts for this second to all endpoints
                cps = 0; // reset count to 0 for next 1-second interval
            }
        }, 0, 1000);
    }

    public void stopSensor(){
        System.out.println("Stopping Geiger Counter...");

        // If inpin was initialized, then remove its listeners
        if (inpin != null){
            inpin.removeAllListeners();
            GPIOInitializer.getInstance().getGpioController().unprovisionPin(inpin);
        }

        // Stop the update timer and set cps to 0
        COUNT_TIMER.cancel();
        cps = 0;

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
}
