package sensor;


import java.io.IOException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.gpio.GPIOService;
import org.eclipse.kura.gpio.KuraClosedDeviceException;
import org.eclipse.kura.gpio.KuraGPIODirection;
import org.eclipse.kura.gpio.KuraGPIOMode;
import org.eclipse.kura.gpio.KuraGPIOPin;
import org.eclipse.kura.gpio.KuraGPIOTrigger;
import org.eclipse.kura.gpio.KuraUnavailableDeviceException;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DistanceSensorConfigurable implements ConfigurableComponent {

	private GPIOService gpioService;
    private List<KuraGPIOPin> openedPins = new ArrayList<>();
    
	private static final Logger s_logger = LoggerFactory.getLogger(DistanceSensorConfigurable.class);
    private static final String APP_ID = "edit.DistanceSensor";
    private Map<String, Object> properties;
    
    private ScheduledFuture<?> blinkTask = null;
    private ScheduledFuture<?> pollTask = null;
    
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private boolean value;
    
    public void setGPIOService(GPIOService gpioService) {
        this.gpioService = gpioService;
    }

    public void unsetGPIOService(GPIOService gpioService) {
        this.gpioService = null;
    }

    protected void activate(ComponentContext componentContext) {
        s_logger.info("Bundle " + APP_ID + " has started!\nWORING!\nWORKING\nWORKING");
    }

    protected void deactivate(ComponentContext componentContext) {
        s_logger.info("Bundle " + APP_ID + " has stopped!");
        releasePins();
    }

    public void updated(Map<String, Object> properties) {
        this.properties = properties;
        releasePins();
        openedPins.clear();
        if(properties != null && !properties.isEmpty()) {
            Iterator<Entry<String, Object>> it = properties.entrySet().iterator();
            while (it.hasNext()) {
                Entry<String, Object> entry = it.next();
                s_logger.info("New property - " + entry.getKey() + " = " +
                entry.getValue() + " of type " + entry.getValue().getClass().toString());
            }
        }
        if (this.gpioService != null) {
        	s_logger.info("______________________________");
        	s_logger.info("Available GPIOs on the system:");
            Map<Integer, String> gpios = this.gpioService.getAvailablePins();
            for (Entry<Integer, String> e : gpios.entrySet()) {
            	s_logger.info("#{} - [{}]", e.getKey(), e.getValue());
            }
            s_logger.info("______________________________");
            acquirePins();
        }
    }
    
    private void readDistance() {
    	for(int i = 0; i < openedPins.size(); i++) {
    		if(openedPins.get(i).getIndex() % 2 == 0) {
    			KuraGPIOPin echo = openedPins.get(i);
    			KuraGPIOPin trigger = openedPins.get(i + 1);
    			try {
    				trigger.setValue(false);
					Thread.sleep(50);
					trigger.setValue(true);
					Thread.sleep(1);
					trigger.setValue(false);
					long start = 0;
					long end = 0;
					while(echo.getValue() == false) {
						start = System.currentTimeMillis();
					}
					while(echo.getValue() == true) {
						end = System.currentTimeMillis();
					}
					long duration = end - start;
					double distance = Math.ceil((duration / 1000000000.0) * 17150);
					s_logger.info("Measured distance: " + distance);
				} catch (KuraUnavailableDeviceException | KuraClosedDeviceException | IOException | InterruptedException e) {
					e.printStackTrace();
				}
    		}
    	}
    }
    
    private void acquirePins() {
        if (this.gpioService != null) {
        	s_logger.info("______________________________");
        	s_logger.info("Available GPIOs on the system:");
            Map<Integer, String> gpios = this.gpioService.getAvailablePins();
            for (Entry<Integer, String> e : gpios.entrySet()) {
            	s_logger.info("#{} - [{}]", e.getKey(), e.getValue());
            }
            s_logger.info("______________________________");
            getPins();
        }
    }
    
    private void getPins() {
        String[] pins = (String[]) this.properties.get("gpio.pins");
        Integer[] directions = (Integer[]) this.properties.get("gpio.directions");
        Integer[] modes = (Integer[]) this.properties.get("gpio.modes");
        Integer[] triggers = (Integer[]) this.properties.get("gpio.triggers");
        for (int i = 0; i < pins.length; i++) {
            try {
            	s_logger.info("Acquiring GPIO pin {} with params:", pins[i]);
            	s_logger.info("   Direction....: {}", directions[i]);
            	s_logger.info("   Mode.........: {}", modes[i]);
            	s_logger.info("   Trigger......: {}", triggers[i]);
                KuraGPIOPin p = getPin(pins[i], getPinDirection(directions[i]), getPinMode(modes[i]),
                        getPinTrigger(triggers[i]));
                if (p != null) {
                	p.open();
                	openedPins.add(p);
                    s_logger.info("GPIO pin {} acquired", pins[i]);
                } else {
                	s_logger.info("GPIO pin {} not found", pins[i]);
                }
            } catch (IOException e) {
            	s_logger.error("I/O Error occurred!", e);
            } catch (Exception e) {
            	s_logger.error("got errror", e);
            }
        }
    }
    
    private KuraGPIOPin getPin(String resource, KuraGPIODirection pinDirection, KuraGPIOMode pinMode,
            KuraGPIOTrigger pinTrigger) {
        KuraGPIOPin pin = null;
        try {
            int terminal = Integer.parseInt(resource);
            if (terminal > 0 && terminal < 1255) {
                pin = this.gpioService.getPinByTerminal(Integer.parseInt(resource), pinDirection, pinMode, pinTrigger);
            }
        } catch (NumberFormatException e) {
            pin = this.gpioService.getPinByName(resource, pinDirection, pinMode, pinTrigger);
        }
        return pin;
    }
    
    private void releasePins() {
    	openedPins.stream().forEach( pin -> {
    		try {
    			s_logger.warn("Closing pin {}", pin);
    			pin.close();
    		} catch (IOException e) {
    			s_logger.warn("Cannot close pin");
    		}
    	});
    }
    
    private KuraGPIODirection getPinDirection(int direction) {
        switch (direction) {
        case 0:
        case 2:
            return KuraGPIODirection.INPUT;
        case 1:
        case 3:
            return KuraGPIODirection.OUTPUT;
        default:
            return KuraGPIODirection.OUTPUT;
        }
    }
    
    private KuraGPIOMode getPinMode(int mode) {
        switch (mode) {
        case 2:
            return KuraGPIOMode.INPUT_PULL_DOWN;
        case 1:
            return KuraGPIOMode.INPUT_PULL_UP;
        case 8:
            return KuraGPIOMode.OUTPUT_OPEN_DRAIN;
        case 4:
            return KuraGPIOMode.OUTPUT_PUSH_PULL;
        default:
            return KuraGPIOMode.OUTPUT_OPEN_DRAIN;
        }
    }
    
    private KuraGPIOTrigger getPinTrigger(int trigger) {
        switch (trigger) {
        case 0:
            return KuraGPIOTrigger.NONE;
        case 2:
            return KuraGPIOTrigger.RAISING_EDGE;
        case 3:
            return KuraGPIOTrigger.BOTH_EDGES;
        case 1:
            return KuraGPIOTrigger.FALLING_EDGE;
        default:
            return KuraGPIOTrigger.NONE;
        }
    }
    
    private void logException(KuraGPIOPin pin, Exception e) {
        if (e instanceof KuraUnavailableDeviceException) {
            s_logger.warn("GPIO pin {} is not available for export.", pin);
        } else if (e instanceof KuraClosedDeviceException) {
        	s_logger.warn("GPIO pin {} has been closed.", pin);
        } else {
        	s_logger.error("I/O Error occurred!", e);
        }
    }
}
