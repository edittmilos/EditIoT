package sensor;


import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.kura.configuration.ConfigurableComponent;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DistanceSensorConfigurable implements ConfigurableComponent {

	private static final Logger s_logger = LoggerFactory.getLogger(DistanceSensorConfigurable.class);
    private static final String APP_ID = "org.eclipse.kura.example.configurable.ConfigurableExample";
    private Map<String, Object> properties;

    protected void activate(ComponentContext componentContext) {
        s_logger.info("Bundle " + APP_ID + " has started!");
    }

    protected void activate(ComponentContext componentContext, Map<String, Object> properties) {
        s_logger.info("Bundle " + APP_ID + " has started with config!");
        updated(properties);
    }

    protected void deactivate(ComponentContext componentContext) {
        s_logger.info("Bundle " + APP_ID + " has stopped!");
    }

    public void updated(Map<String, Object> properties) {
        this.properties = properties;
        if(properties != null && !properties.isEmpty()) {
            Iterator<Entry<String, Object>> it = properties.entrySet().iterator();
            while (it.hasNext()) {
                Entry<String, Object> entry = it.next();
                s_logger.info("New property - " + entry.getKey() + " = " +
                entry.getValue() + " of type " + entry.getValue().getClass().toString());
            }
        }
    }
}
