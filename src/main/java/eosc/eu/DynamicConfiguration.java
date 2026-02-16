package eosc.eu;

import org.jboss.logging.Logger;
import org.eclipse.microprofile.config.spi.ConfigSource;
import io.quarkus.runtime.annotations.StaticInitSafe;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Random;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;


@StaticInitSafe
public class DynamicConfiguration implements ConfigSource {

    private static final Logger log = Logger.getLogger(DynamicConfiguration.class);

    private static final String instanceConfigProperty = "eosc.service.instance";
    private static final Map<String, String> configuration = new HashMap<>();

    public DynamicConfiguration() {
        initInstance();
    }

    synchronized static void initInstance() {
        String instance = configuration.get(instanceConfigProperty);
        if(null == instance) {
            // Get machine hostname
            try {
                ProcessBuilder pb = new ProcessBuilder("/bin/uname", "--nodename");
                Process p = pb.start();

                instance = new BufferedReader(
                        new InputStreamReader(pb.start().getInputStream())
                ).readLine();
            } catch(Exception e) {
                // Could not get hostname
                log.error(e);
                log.info("Fallback to random instance name");

                // Fallback to a random string
                int leftLimit = 97;     // 'a'
                int rightLimit = 122;   // 'z'
                Random random = new Random();

                instance = random.ints(leftLimit, rightLimit + 1)
                        .limit(12)
                        .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                        .toString();
            }

            log.infof("Service instance: %s", instance);

            // Store it as the instance configuration property
            configuration.put(instanceConfigProperty, instance);
        }
    }

    @Override
    public int getOrdinal() {
        return 275;
    }

    @Override
    public Set<String> getPropertyNames() {
        return configuration.keySet();
    }

    @Override
    public String getValue(final String propertyName) {

        if(propertyName.equals("quarkus.otel.resource.attributes")) {

            String instance = configuration.get(instanceConfigProperty);
            return String.format("service.namespace=eosc,service.name=data-transfer,service.instance=%s", instance);
        }

        return configuration.get(propertyName);
    }

    @Override
    public String getName() {
        return DynamicConfiguration.class.getSimpleName();
    }
}
