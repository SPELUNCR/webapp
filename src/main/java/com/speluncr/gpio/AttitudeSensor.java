package com.speluncr.gpio;

import com.pi4j.io.gpio.*;
import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;
import com.speluncr.websocket.AttitudeEndpoint;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Timer;
import java.util.TimerTask;
import static java.lang.Math.*;

/************************************************************
 * This class will initialize the MPU6050 attitude sensor,
 * then read data and broadcast the data on all
 * AttitudeEnpoints at 30 Hz.
 *
 * THE LOGICAL PIN NUMBERS DO NOT ALWAYS MATCH THE PHYSICAL
 * PIN NUMBERS. Refer to the pi4j pin diagram at the url:
 * https://pi4j.com/1.4/pins/rpi-4b.html
 ************************************************************/
public class AttitudeSensor implements Sensor{
    private long lastMeasTime = System.nanoTime();
    private I2CBus bus = null;
    private I2CDevice MPU6050 = null;
    private final Timer POLLING_TIMER = new Timer("Attitude Polling Timer");
    private boolean running = false;

    public synchronized void startSensor(){
        final int MPU6050_ADDR  = 0x69; // 0x68 when ADO set low. 0x69 when ADO set high
        final int CONFIG        = 0x1A; // framesync and low pass filtering (use 3)
        final int SMPLRT_DIV    = 0x19; // Sample Rate (8 or 1 kHz) = Gyroscope Output Rate / (1 + SMPLRT_DIV)
        final int GYRO_CONFIG   = 0x1B;
        final int ACCEL_CONFIG  = 0x1C;
        final int INT_PIN_CFG   = 0x37; // Interrupt pin configuration
        final int INT_ENABLE    = 0x38; // Register enables interrupt generation
        final int PWR_MGMT_1    = 0x6B; // Power management 1
        final int PWR_MGMT_2    = 0x6C; // Power management 2

        // Don't execute this method if the sensor is already running
        if (running){
            return;
        }

        // Set ADO pin high (address = 0x69). GPIO_16 is physical pin 10
        final GpioPinDigitalOutput ADO = GPIOInitializer.getInstance().getGpioController()
                .provisionDigitalOutputPin(RaspiPin.GPIO_16, PinState.HIGH);

        // Set shutdown state of ADO to low
        ADO.setShutdownOptions(true, PinState.LOW);

        // Open I2C bus for communication with MPU6050
        try {
            bus = I2CFactory.getInstance(I2CBus.BUS_1);
            MPU6050 = bus.getDevice(MPU6050_ADDR);
        } catch (I2CFactory.UnsupportedBusNumberException | IOException e){
            System.err.println("initializeSensor(): Failed to get bus or device.");
            e.printStackTrace();
            return;
        }

        // Configure the sensor by writing to registers. See register map for more info.
        try {
            MPU6050.write(PWR_MGMT_1, (byte) 0x00); // no reset, no sleep, no cycle, default clock
            MPU6050.write(PWR_MGMT_2, (byte) 0x00); // No standby mode and 1.25 Hz wake-up frequency (not applicable)
            MPU6050.write(CONFIG, (byte) 0x06); // Disable external frame sync and enable low-pass filter
            MPU6050.write(SMPLRT_DIV, (byte) 0x20); // Set SMPLRT_DIV = 32 for 30.3 Hz sample rate
            MPU6050.write(GYRO_CONFIG, (byte) 0x10); // Set full scale range to +/- 1000 deg/s
            MPU6050.write(ACCEL_CONFIG, (byte) 0x00); // Set full scale range to +/- 2g
            MPU6050.write(INT_PIN_CFG, (byte) 0x00); // active high, push-pull, high until status read
            MPU6050.write(INT_ENABLE, (byte) 0x00); // Enable data ready interrupts on interrupt pin (open drain)
            System.out.println("Sensor configuration registers have been set.");
        } catch (IOException e){
            System.err.println("initializeSensor(): Failed to set configuration registers.");
            e.printStackTrace();
            return;
        }
        lastMeasTime = System.nanoTime(); // Used to find time difference for integrating gyro data

        POLLING_TIMER.schedule(new TimerTask() {
            @Override
            public void run() {
                AttitudeEndpoint.broadcast(getAttitude());
            }
        }, 0, 33); // Immediately start reading attitude data every 33 ms (30.3 Hz)
        running = true;
    }

    public synchronized void stopSensor(){
        // Don't execute this method if sensor is not running
        if (!running) {
            return;
        }

        POLLING_TIMER.cancel();

        // Close I2C bus if open
        if (bus != null){
            try{
                bus.close();
            } catch (IOException e){
                System.err.println("stopSensor(): Failed to close bus.");
                e.printStackTrace();
            }
        }
        running = false;
    }

    // Read two bytes from the MPU6050 register and cast to float
    private synchronized double read16ToDouble(int addr) throws IOException{
        byte[] bytes = new byte[2];
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        bb.order(ByteOrder.BIG_ENDIAN);
        MPU6050.read(addr, bytes, 0, 2);
        return bb.getShort();
    }

    // Read the MPU6050 sensor registers and return a ByteBuffer of floats
    // The order of returned values is: accX, accY, accZ, gyrX, gyrY, gyrZ, temp
    private synchronized ByteBuffer getAttitude(){
        final int TEMP_OUT      = 0x41; // 16-bit signed value (0x41-0x42)
        final int ACCEL_X       = 0x3B; // 16-bit 2's comp. value (0x3B-0x3C)
        final int ACCEL_Y       = 0x3D; // 16-bit 2's comp. value (0x3D-0x3E)
        final int ACCEL_Z       = 0x3F; // 16-bit 2's comp. value (0x3F-0x40)
        final int GYRO_X        = 0x43; // 16-bit 2's comp. value (0x43-0x44)
        final int GYRO_Y        = 0x45; // 16-bit 2's comp. value (0x45-0x46)
        // final int GYRO_Z        = 0x47; // 16-bit 2's comp. value (0x47-0x48)
        final double ACC_SCALE = 16384; // LSB/g for +/- 2g range
        final double GYR_SCALE = 32.8f; // LSB/deg/s for +/- 1000 deg/s range

        // Gyro, Accelerometer and Temperature values in big-endian
        double accX = 0, accY = 0, accZ = 0, gyrX = 0, gyrY = 0, temp = 0; //gyrZ = 0
        ByteBuffer bb = ByteBuffer.wrap(new byte[4*Double.BYTES]);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        try {
            // Read data from MPU6050 registers and convert to g, deg/s, and C
            accX = read16ToDouble(ACCEL_X) / ACC_SCALE;
            accY = read16ToDouble(ACCEL_Y) / ACC_SCALE;
            accZ = read16ToDouble(ACCEL_Z) / ACC_SCALE;
            gyrX = read16ToDouble(GYRO_X) / GYR_SCALE;
            gyrY = read16ToDouble(GYRO_Y) / GYR_SCALE;
            // gyrZ = read16ToDouble(GYRO_Z) / GYR_SCALE;
            temp = read16ToDouble(TEMP_OUT) / 340d + 36.53d; // see register map for this conversion
        } catch (IOException e){
            System.err.println("readAttitudeData(): Failed to read sensor data.");
            e.printStackTrace();
        }

        // Apply complementary filter to combine accel. and gyro to determine attitude
        // Don't care about yaw since only roll and pitch can flip rover
        final double A = 0.9836; // Constant for complementary filter tau = 2s, dt = 1/30
        double roll = 0, pitch = 0, yaw = 0;

        // Calculate time period to integrate gyro data and update measurement time
        long currMeasTime = System.nanoTime();
        double dt = (currMeasTime - lastMeasTime) / (1000000000d); // time since last sample (s)
        lastMeasTime = currMeasTime;

        // Filtered roll and pitch values
        double magAcc = sqrt(pow(accX, 2d)+pow(accY, 2d)+pow(accZ, 2d)); // Magnitude of acceleration vector
        double accRoll = atan2(accY/magAcc, accZ/magAcc);
        double accPitch = asin(-accX/magAcc);
        double gyrRoll = roll + gyrX*dt;
        double gyrPitch = pitch + gyrY*dt;
        roll = (1 - A)*(gyrRoll) + A*accRoll;
        pitch = (1 - A)*(gyrPitch) + A*accPitch;
        // System.out.printf("AccRoll = %.2f, GyrRoll = %.2f, AccPitch = %.2f, GyrPitch = %.2f, Acc = %.2f,%.2f,%.2f Gyr = %.2f,%.2f\n",
        //        accRoll, gyrRoll, accPitch, gyrPitch, accX, accY, accZ, gyrX, gyrY);

        // Put data from MPU6050 registers into byte buffer to return
        //System.out.printf("R = %.2f, P = %.2f, Y = %.2f\n", toDegrees(roll), toDegrees(pitch), toDegrees(yaw));
        bb.putDouble(roll);
        bb.putDouble(pitch);
        bb.putDouble(yaw);
        bb.putDouble(temp);
        return bb;
    }
}