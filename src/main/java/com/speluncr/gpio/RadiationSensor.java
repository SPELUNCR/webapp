package com.speluncr.gpio;

import com.pi4j.wiringpi.Gpio;
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
    private final int INTERRUPT_PIN = 7;
    private final Timer COUNT_TIMER = new Timer("Radiation Update Timer");
    private final TelemetryServlet servlet;
    private DataOutputStream outputStream = null;
    private long initTime = System.nanoTime(); // time when sensor started
    private int cps = 0; // counts in the current 1-second period
    private boolean running = false;

    public RadiationSensor(TelemetryServlet telemetryServlet){
        servlet = telemetryServlet;
    }

    public synchronized void startSensor(){
        // Don't execute this method if sensor is already running
        if (running){
            return;
        }

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

        // Setup wiring pi
        if (Gpio.wiringPiSetup() == -1){
            System.err.println("[ERROR] GPIO setup failed.");
            return;
        }

        // Set initial time
        initTime = System.nanoTime();

        // Configure input pin 7, activate pull-up resistor and attach interrupt callback method
        Gpio.pinMode(INTERRUPT_PIN, Gpio.INPUT);
        Gpio.pullUpDnControl(INTERRUPT_PIN, Gpio.PUD_UP);
        Gpio.wiringPiISR(INTERRUPT_PIN, Gpio.INT_EDGE_FALLING, i -> incrementCount());

        COUNT_TIMER.schedule(new TimerTask() {
            @Override
            public synchronized void run() {
                RadiationEndpoint.broadcast(cps); // send the number of counts for this second to all endpoints
                cps = 0; // reset count to 0 for next 1-second interval
            }
        }, 0, 1000);
        running = true;
    }

    public synchronized void stopSensor(){
        // Don't execute this method if sensor is already stopped
        if (!running){
            return;
        }

        Gpio.wiringPiClearISR(INTERRUPT_PIN);

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
        running = false;
    }

    private synchronized void incrementCount(){
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
}
