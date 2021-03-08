package com.speluncr;

import com.speluncr.gpio.AttitudeSensor;
import com.speluncr.gpio.RadiationSensor;
import javax.servlet.http.HttpServlet;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;

public class TelemetryServlet extends HttpServlet {
    private final RadiationSensor gc = new RadiationSensor(this);
    private final AttitudeSensor as = new AttitudeSensor();
    private final Properties properties = new Properties();

    public TelemetryServlet(){
        LoadProperties();
        System.out.println("Telemetry servlet constructed");
    }

    @Override
    public void init() {
        gc.initializeSensor();
        System.out.println("Geiger Counter Initialized");
        as.initializeSensor();
        System.out.println("Attitude Sensor Initialized");
    }

    @Override
    public void destroy() {
        gc.stopSensor();
        System.out.println("Geiger Counter Stopped");
        as.stopSensor();
        System.out.println("Attitude Sensor Stopped");
    }

    private void LoadProperties(){
        // Navigate to the servlet.conf file that contains properties used by this class
        // The following try-catch block is only necessary because the absolute path
        // of servlet.conf will be different on other computers and I want this to work
        // right away so you don't have to hard-code your specific path and recompile this code.
        File propertiesFile = new File("/opt/apache-tomcat-9.0.39/webapps/speluncr/WEB-INF/servlet.conf");
        System.out.format("Attempting to load properties from: %s%n", propertiesFile.getAbsolutePath());

        // Stop if there's no file to load the properties
        if (!propertiesFile.exists()){
            System.err.println("[ERROR]: The properties file could not be read");
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
