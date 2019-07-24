package sensor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import org.eclipse.kura.KuraException;
import org.eclipse.kura.gpio.KuraGPIOPin;
import org.slf4j.Logger;

public class Reader extends Thread {

	private static final Object locker = new Object();
	private Map<String, Double> values;
	private Logger s_logger;
	private KuraGPIOPin echo;
	private KuraGPIOPin trigger;
	private int index;

	public Reader(int index, KuraGPIOPin echo, KuraGPIOPin trigger,Map<String, Double> values, Logger s_logger) {
		setDaemon(true);
		this.echo = echo;
		this.trigger = trigger;
		this.s_logger = s_logger;
		this.values = values;
		this.index = index;
	}

	public void run() {
		ArrayList<Double> measurements = new ArrayList<>();
		long measureStart = System.currentTimeMillis();
		while (System.currentTimeMillis() - measureStart < 500) {
			try {
				long start = 0;
				long end = 0;
				synchronized (locker) {
					trigger.setValue(false);
					Thread.sleep(10);
					trigger.setValue(true);
					Thread.sleep(1);
					trigger.setValue(false);
					long control = System.nanoTime();
					while (echo.getValue() == false && (start - control < 15000000)) {
						start = System.nanoTime();
					}
					control = System.nanoTime();
					while (echo.getValue() == true && (start - control < 15000000)) {
						end = System.nanoTime();
					}
				}
				long duration = end - start;
				double distance = Math.ceil((duration * 17150.0) / 1000000000.0);
				measurements.add(distance);
			} catch (IOException | InterruptedException | KuraException e) {
				e.printStackTrace();
			}
		}
		double median = getMedian(measurements);
		s_logger.info("Measured distance from " + index + ": " + median);
		if (median < 0 || median > 2000)
			median = 50;
		values.put(String.valueOf(index), median);
	}

	public double getMedian(ArrayList<Double> values) {
		values.sort((x, y) -> {
			if (y > x)
				return 1;
			else if (y == x)
				return 0;
			else
				return -1;
		});
		double median = 0;
		if (values.size() % 2 == 1)
			median = values.get(values.size() / 2);
		else
			median = (values.get(values.size() / 2 - 1) + values.get(values.size() / 2 + 1)) / 2;
		return median;
	}
}
