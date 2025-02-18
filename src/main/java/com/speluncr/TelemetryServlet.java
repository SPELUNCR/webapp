package com.speluncr;

import com.speluncr.gpio.AttitudeSensor;
import com.speluncr.gpio.GPIOInitializer;
import com.speluncr.gpio.RadiationSensor;
import javax.servlet.http.HttpServlet;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

public class TelemetryServlet extends HttpServlet {
    private final RadiationSensor gc = new RadiationSensor(this);
    private final AttitudeSensor as = new AttitudeSensor();
    private final Properties properties = new Properties();

    public TelemetryServlet(){
        System.out.println("Telemetry servlet constructed");
    }

    @Override
    public void init() {
        LoadProperties();
        gc.startSensor();
        System.out.println("Geiger Counter Initialized");
        as.startSensor();
        System.out.println("Attitude Sensor Initialized");
    }

    @Override
    public void destroy() {
        gc.stopSensor();
        System.out.println("Geiger Counter Stopped");
        as.stopSensor();
        System.out.println("Attitude Sensor Stopped");
        GPIOInitializer.getInstance().getGpioController().shutdown();
    }

    private void LoadProperties(){
        // Navigate to the servlet.conf file that contains properties used by this class
        // The following try-catch block is only necessary because the absolute path
        // of servlet.conf will be different on other computers and I want this to work
        // right away so you don't have to hard-code your specific path and recompile this code.
        Path servletRoot = Path.of(getServletContext().getRealPath("/"));
        File propertiesFile = servletRoot.resolve("WEB-INF/servlet.conf").toFile();
        System.out.format("Attempting to load properties from: %s%n", propertiesFile.getAbsolutePath());

        // Stop if there's no file to load the properties
        if (!propertiesFile.exists()){
            System.err.println("[INFO]: The properties file could not be read. Using defaults");
            properties.setProperty("RadiationSaveDirectory", System.getProperty("user.home"));
            return;
        }

        // Load properties from file
        try (BufferedReader br = new BufferedReader(new FileReader(propertiesFile))){
            properties.load(br);
            System.out.println("[SUCCESS]: Properties loaded from file");
        } catch (IOException e){
            e.printStackTrace();
        }
    }

    public Properties getProperties(){
        return properties;
    }
}
