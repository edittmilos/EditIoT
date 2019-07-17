package sensor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.cloudconnection.listener.CloudConnectionListener;
import org.eclipse.kura.cloudconnection.listener.CloudDeliveryListener;
import org.eclipse.kura.cloudconnection.message.KuraMessage;
import org.eclipse.kura.cloudconnection.publisher.CloudPublisher;
import org.eclipse.kura.cloudconnection.subscriber.listener.CloudSubscriberListener;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.gpio.GPIOService;
import org.eclipse.kura.gpio.KuraGPIODirection;
import org.eclipse.kura.gpio.KuraGPIOMode;
import org.eclipse.kura.gpio.KuraGPIOPin;
import org.eclipse.kura.gpio.KuraGPIOTrigger;
import org.eclipse.kura.message.KuraPayload;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DistanceSensorConfigurable
		implements ConfigurableComponent, CloudConnectionListener, CloudDeliveryListener, CloudSubscriberListener {

	private GPIOService gpioService;
	private CloudPublisher cloudPublisher;
	private List<KuraGPIOPin> openedPins = new ArrayList<>();

	private static final Logger s_logger = LoggerFactory.getLogger(DistanceSensorConfigurable.class);
	private static final String APP_ID = "edit.DistanceSensor";
	private Map<String, Object> properties;

	private ScheduledExecutorService executor;

	public void setGPIOService(GPIOService gpioService) {
		this.gpioService = gpioService;
	}

	public void unsetGPIOService(GPIOService gpioService) {
		this.gpioService = null;
	}

	public void setCloudPublisher(CloudPublisher cloudPublisher) {
		this.cloudPublisher = cloudPublisher;
		this.cloudPublisher.registerCloudConnectionListener(DistanceSensorConfigurable.this);
		this.cloudPublisher.registerCloudDeliveryListener(DistanceSensorConfigurable.this);
	}

	public void unsetCloudPublisher(CloudPublisher cloudPublisher) {
		this.cloudPublisher.unregisterCloudConnectionListener(DistanceSensorConfigurable.this);
		this.cloudPublisher.unregisterCloudDeliveryListener(DistanceSensorConfigurable.this);
		this.cloudPublisher = null;
	}

	protected void activate(ComponentContext componentContext) {
		s_logger.info("Bundle " + APP_ID + " has started!\nWORKING!\nWORKING!\nWORKING!");
	}

	protected void deactivate(ComponentContext componentContext) {
		releasePins();
		s_logger.info("Bundle " + APP_ID + " has stopped!");
	}

	public void updated(Map<String, Object> properties) {

		this.properties = properties;
		releasePins();
		openedPins.clear();

		if (((Boolean) properties.get("turnOn")) == true) {
			acquirePins();
			getPins();
			executor = Executors.newScheduledThreadPool(0);
			executor.scheduleAtFixedRate(() -> readDistance(), 0, 1, TimeUnit.SECONDS);
		} else
			executor.shutdown();
	}

	private void readDistance() {
		KuraPayload payload = new KuraPayload();
		for (int i = 0; i < openedPins.size(); i++) {
			if (openedPins.get(i).getIndex() % 2 == 0) {
				KuraGPIOPin echo = openedPins.get(i);
				KuraGPIOPin trigger = openedPins.get(i + 1);
				try {
					long start = 0;
					long end = 0;
					trigger.setValue(false);
					Thread.sleep(2000);
					trigger.setValue(true);
					Thread.sleep((long) 0.001);
					trigger.setValue(false);
					while (echo.getValue() == false) {
						start = System.nanoTime();
					}
					while (echo.getValue() == true) {
						end = System.nanoTime();
					}
					long duration = end - start;
					double distance = Math.ceil((duration * 17150.0) / 1000000000.0);
					s_logger.info("Measured distance: " + distance);
					payload.addMetric("Distance [cm]", String.valueOf(distance));
				} catch (IOException | InterruptedException | KuraException e) {
					e.printStackTrace();
				}
			}
		}
		if (!payload.metrics().isEmpty()) {
			KuraMessage message = new KuraMessage(payload);
			try {
				cloudPublisher.publish(message);
			} catch (KuraException e) {
				e.printStackTrace();
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
		}
	}

	private void getPins() {
		String[] pins1 = (String[]) this.properties.get("gpio.pins1");
		String[] pins2 = (String[]) this.properties.get("gpio.pins2");
		String[] pins = Arrays.copyOf(concat(pins1, pins2), pins1.length + pins2.length, String[].class);
		Integer[] dirs1 = (Integer[]) this.properties.get("gpio.directions1");
		Integer[] dirs2 = (Integer[]) this.properties.get("gpio.directions2");
		Integer[] directions = Arrays.copyOf(concat(dirs1, dirs2), dirs1.length + dirs2.length, Integer[].class);
		Integer[] modes1 = (Integer[]) this.properties.get("gpio.modes1");
		Integer[] modes2 = (Integer[]) this.properties.get("gpio.modes2");
		Integer[] modes = Arrays.copyOf(concat(modes1, modes2), modes1.length + modes2.length, Integer[].class);
		Integer[] trigs1 = (Integer[]) this.properties.get("gpio.triggers1");
		Integer[] trigs2 = (Integer[]) this.properties.get("gpio.triggers2");
		Integer[] triggers = Arrays.copyOf(concat(trigs1, trigs2), trigs1.length + trigs2.length, Integer[].class);
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

	private Object[] concat(Object[] array1, Object[] array2) {
		Object[] array = new Object[array1.length + array2.length];
		int i = 0;
		for (; i < array1.length; i++)
			array[i] = array1[i];
		for (int j = 0; j < array2.length; j++) {
			array[i + j] = array2[j];
		}
		return array;
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

	private void releasePins() {
		s_logger.warn("CLOSING PINSSSS\n\n");
		openedPins.forEach(pin -> {
			try {
				s_logger.warn("Closing GPIO pin {}", pin);
				pin.close();
			} catch (IOException e) {
				s_logger.warn("Cannot close pin!");
			}
		});
		openedPins.clear();
	}

	@Override
	public void onMessageConfirmed(String arg0) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onConnectionEstablished() {
		// TODO Auto-generated method stub

	}

	@Override
	public void onConnectionLost() {
		// TODO Auto-generated method stub

	}

	@Override
	public void onDisconnected() {
		// TODO Auto-generated method stub

	}

	@Override
	public void onMessageArrived(KuraMessage arg0) {
		// TODO Auto-generated method stub

	}
}
