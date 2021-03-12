package com.speluncr.gpio;

import com.pi4j.io.gpio.*;
import com.pi4j.io.gpio.event.GpioPinListenerDigital;
import com.pi4j.io.i2c.I2CBus;
import com.pi4j.io.i2c.I2CDevice;
import com.pi4j.io.i2c.I2CFactory;
import com.speluncr.websocket.AttitudeEndpoint;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/************************************************************
 * This class will initialize the MPU6050 attitude sensor,
 * read data when it arrives and broadcast the data on all
 * AttitudeEnpoints.
 *
 * THE LOGICAL PIN NUMBERS DO NOT ALWAYS MATCH THE PHYSICAL
 * PIN NUMBERS. Refer to the pi4j pin diagram at the url:
 * https://pi4j.com/1.4/pins/rpi-4b.html
 ************************************************************/
public class AttitudeSensor implements Sensor{
    private I2CBus bus = null;
    private I2CDevice MPU6050 = null;
    private GpioPinDigitalInput interrupt = null;

    public void initializeSensor(){
        final int MPU6050_ADDR  = 0x69; // 0x68 when ADO set low. 0x69 when ADO set high
        final int CONFIG        = 0x1A; // framesync and low pass filtering (use 3)
        final int SMPLRT_DIV    = 0x19; // Sample Rate (8 or 1 kHz) = Gyroscope Output Rate / (1 + SMPLRT_DIV)
        final int GYRO_CONFIG   = 0x1B;
        final int ACCEL_CONFIG  = 0x1C;
        final int INT_PIN_CFG   = 0x37; // Interrupt pin configuration
        final int INT_ENABLE    = 0x38; // Register enables interrupt generation
        final int PWR_MGMT_1    = 0x6B; // Power management 1
        final int PWR_MGMT_2    = 0x6C; // Power management 2

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
            MPU6050.write(INT_ENABLE, (byte) 0x01); // Enable data ready interrupts on interrupt pin (open drain)
            System.out.println("Sensor configuration registers have been set.");
        } catch (IOException e){
            System.err.println("initializeSensor(): Failed to set configuration registers.");
            e.printStackTrace();
            return;
        }

        // Configure pin as interrupt GPIO_15 is physical pin 8
        interrupt = GPIOInitializer.getInstance().getGpioController()
                .provisionDigitalInputPin(RaspiPin.GPIO_15, PinPullResistance.OFF);

        // Add listener for when interrupt pin goes low. This means data is ready.
        interrupt.addListener((GpioPinListenerDigital) event -> {
            if (event.getState().isHigh()){
                readAttitudeData();
            }
        });
    }

    public void stopSensor(){
        // If interrupt was initialized, remove all its listeners
        if (interrupt != null){
            interrupt.removeAllListeners();
        }

        // Close I2C bus if open
        if (bus != null){
            try{
                bus.close();
            } catch (IOException e){
                System.err.println("stopSensor(): Failed to close bus.");
                e.printStackTrace();
            }
        }
    }

    private float read16ToFloat(int addr) throws IOException{
        byte[] bytes = new byte[2];
        ByteBuffer bb = ByteBuffer.wrap(bytes);
        bb.order(ByteOrder.BIG_ENDIAN);
        MPU6050.read(addr, bytes, 0, 2);
        return bb.getShort();
    }

    private void readAttitudeData(){
        final int INT_STATUS    = 0x3A; // interrupt status register
        final int TEMP_OUT      = 0x41; // 16-bit signed value (0x41-0x42)
        final int ACCEL_X       = 0x3B; // 16-bit 2's comp. value (0x3B-0x3C)
        final int ACCEL_Y       = 0x3D; // 16-bit 2's comp. value (0x3D-0x3E)
        final int ACCEL_Z       = 0x3F; // 16-bit 2's comp. value (0x3F-0x40)
        final int GYRO_X        = 0x43; // 16-bit 2's comp. value (0x43-0x44)
        final int GYRO_Y        = 0x45; // 16-bit 2's comp. value (0x45-0x46)
        final int GYRO_Z        = 0x47; // 16-bit 2's comp. value (0x47-0x48)
        final float ACC_SCALE = 16384; // LSB/g for +/- 2g range
        final float GYR_SCALE = 32.8f; // LSB/deg/s for +/- 1000 deg/s range

        // Gyro, Accelerometer and Temperature values in big-endian
        float accX, accY, accZ, gyrX, gyrY, gyrZ, temp;
        ByteBuffer bb = ByteBuffer.wrap(new byte[28]);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        try {
            // Read data from MPU6050 registers and convert to g, deg/s, and C
            MPU6050.read(INT_STATUS); // reset int status by reading INT_STATUS register
            accX = read16ToFloat(ACCEL_X) / ACC_SCALE;
            accY = read16ToFloat(ACCEL_Y) / ACC_SCALE;
            accZ = read16ToFloat(ACCEL_Z) / ACC_SCALE;
            gyrX = read16ToFloat(GYRO_X) / GYR_SCALE;
            gyrY = read16ToFloat(GYRO_Y) / GYR_SCALE;
            gyrZ = read16ToFloat(GYRO_Z) / GYR_SCALE;
            temp = read16ToFloat(TEMP_OUT) / 340f + 36.53f; // see register map for this conversion
        } catch (IOException e){
            System.err.println("interruptListener: Failed to read sensor data.");
            e.printStackTrace();
            return;
        }

        // Put data from MPU6050 registers into byte buffer to broadcast on endpoints
        bb.putFloat(accX);
        bb.putFloat(accY);
        bb.putFloat(accZ);
        bb.putFloat(gyrX);
        bb.putFloat(gyrY);
        bb.putFloat(gyrZ);
        bb.putFloat(temp);
        bb.position(0); // The websocket sendBinary() method doesn't seem to like other positions
        //System.out.println("Broadcasting attitude data...");
        AttitudeEndpoint.broadcast(bb);
    }
}