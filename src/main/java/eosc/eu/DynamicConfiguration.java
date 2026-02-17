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


/***
 * Class for dynamically computed configuration properties.
 */
@StaticInitSafe
public class DynamicConfiguration implements ConfigSource {

    private static final Logger log = Logger.getLogger(DynamicConfiguration.class);

    private static final String instanceConfigProperty = "eosc.service.instance";
    private static final Map<String, String> configuration = new HashMap<>();

    /***
     * Constructor
     */
    public DynamicConfiguration() {
        initInstance();
    }

    /***
     * Initialize instance name (use machine hostname)
     */
    static synchronized void initInstance() {
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

    /***
     * Returns the priority of this configuration source.
     * If a property is specified in multiple config sources, the value in the config source with the
     * highest ordinal takes precedence.
     * @return Configuration source priority
     */
    @Override
    public int getOrdinal() {
        return 275;
    }

    /***
     * Gets all property names known to this configuration source, without evaluating the values.
     * The returned set is not required to allow concurrent iteration; however, if the same set is returned
     * by multiple calls to this method, then the implementation must support concurrent iteration.
     * The set of keys returned may be a point-in-time snapshot, or may change over time.
     * @return Property names that are known to this configuration source
     */
    @Override
    public Set<String> getPropertyNames() {
        return configuration.keySet();
    }

    /***
     * Get the value for a configuration property.
     * @return The value of the requested property, null if not known by this configuration source.
     */
    @Override
    public String getValue(final String propertyName) {

        if(propertyName.equals("quarkus.otel.resource.attributes")) {

            String instance = configuration.get(instanceConfigProperty);
            return String.format("service.namespace=eosc,service.name=data-transfer,service.instance.id=%s", instance);
        }

        return configuration.get(propertyName);
    }

    /***
     * Gets the name of the configuration source.
     * @return The name of the configuration source
     */
    @Override
    public String getName() {
        return DynamicConfiguration.class.getSimpleName();
    }
}
