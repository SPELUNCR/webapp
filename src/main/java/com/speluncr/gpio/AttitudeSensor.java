package com.speluncr.gpio;

import com.pi4j.io.gpio.GpioPinDigitalInput;
import com.pi4j.io.gpio.PinPullResistance;
import com.pi4j.io.gpio.RaspiPin;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;
import com.speluncr.TelemetryServlet;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/************************************************************
 * This class will initialize the MPU6050 attitude sensor and
 * allow other classes access to the sensor's data
 ************************************************************/
public class AttitudeSensor implements Sensor{
    private final TelemetryServlet servlet;
    private I2CBus bus = null;
    private I2CDevice MPU6050;

    public AttitudeSensor(TelemetryServlet telemetryServlet){
        servlet = telemetryServlet;
    }

    public void initializeSensor(){
        final int MPU6050_ADDRESS = 0x69; // 0x68 when ADO set low. 0x69 when ADO set high
        final int CONFIG = 0x1A; // framesync and low pass filtering (use 3)
        final int SMPLRT_DIV = 0x19; // Sample Rate (8 or 1 kHz) = Gyroscope Output Rate / (1 + SMPLRT_DIV)
        final int GYRO_CONFIG = 0x1B;
        final int ACCEL_CONFIG = 0x1C;
        final int INT_ENABLE = 0x38; // Register enables interrupt generation
        final int INT_STATUS = 0x3A; // interrupt status register
        final int TEMP_OUT = 0x41; // 16-bit signed value (0x41-0x42)
        final int ACCEL_X = 0x3B; // 16-bit 2's comp. value (0x3B-0x3C)
        final int ACCEL_Y = 0x3D; // 16-bit 2's comp. value (0x3D-0x3E)
        final int ACCEL_Z = 0x3F; // 16-bit 2's comp. value (0x3F-0x40)
        final int GYRO_X = 0x43; // 16-bit 2's comp. value (0x43-0x44)
        final int GYRO_Y = 0x45; // 16-bit 2's comp. value (0x45-0x46)
        final int GYRO_Z = 0x47; // 16-bit 2's comp. value (0x47-0x48)

        // Open I2C bus for communication with MPU6050
        try {
            bus = I2CFactory.getInstance(I2CBus.BUS_1);
            MPU6050 = bus.getDevice(MPU6050_ADDRESS);
        } catch (I2CFactory.UnsupportedBusNumberException | IOException e){
            e.printStackTrace();
        }

        // Configure the sensor by writing to registers. See register map for more info.
        try {
            MPU6050.write(CONFIG, (byte) 0x06); // Disable external frame sync and enable low-pass filter
            MPU6050.write(SMPLRT_DIV, (byte) 0x20); // Set SMPLRT_DIV = 32 for 30.3 Hz sample rate
            MPU6050.write(GYRO_CONFIG, (byte) 0x10); // Set full scale range to +/- 1000 deg/s
            MPU6050.write(ACCEL_CONFIG, (byte) 0x00); // Set full scale range to +/- 2g
            MPU6050.write(INT_ENABLE, (byte) 0x01); // Enable data ready interrupts on interrupt pin (open drain)
        } catch (IOException e){
            e.printStackTrace();
        }

        // Configure
        GpioPinDigitalInput interrupt = GPIOInitializer.getInstance().getGpioController()
                .provisionDigitalInputPin(RaspiPin.GPIO_08, PinPullResistance.PULL_UP);
        interrupt.addListener((GpioPinListenerDigital) event -> {
            if (event.getState().isLow()){
                // Gyro, Accelerometer and Temperature values in big-endian
                short accX, accY, accZ, gyrX, gyrY, gyrZ, temp;
                try {
                    MPU6050.read(INT_STATUS); // reset int status by reading INT_STATUS register
                    accX = readShort(ACCEL_X);
                    accY = readShort(ACCEL_Y);
                    accZ = readShort(ACCEL_Z);
                    gyrX = readShort(GYRO_X);
                    gyrY = readShort(GYRO_Y);
                    gyrZ = readShort(GYRO_Z);
                    temp = readShort(TEMP_OUT);
                } catch (IOException e){
                    e.printStackTrace();
                }
            }
        });
    }

    public void stopSensor(){
        // Close I2C bus if open
        if (bus != null){
            try{
                bus.close();
            } catch (IOException e){
                e.printStackTrace();
            }
        }
    }

    private short readShort(int addr) throws IOException{
        byte[] bytes = new byte[2];
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        bb.order(ByteOrder.BIG_ENDIAN);
        MPU6050.read(addr, bytes, 0, 2);
        return bb.getShort();
    }
}