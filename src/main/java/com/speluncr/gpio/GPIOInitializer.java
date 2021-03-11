package com.speluncr.gpio;

import com.pi4j.io.gpio.GpioFactory;
import com.pi4j.io.gpio.GpioController;

/************************************************************
 * This class will create and serve as a reference for the
 * single GPIO controller needed to access the Raspberry Pi's
 * GPIO pins.
 ************************************************************/
public class GPIOInitializer {
    private static final GPIOInitializer gpioInitializer = new GPIOInitializer();
    private static final GpioController gpioController = GpioFactory.getInstance();

    public static GPIOInitializer getInstance(){
        return gpioInitializer;
    }

    public GpioController getGpioController(){
        return gpioController;
    }
}
