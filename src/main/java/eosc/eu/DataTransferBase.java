package eosc.eu;

import org.jboss.logging.Logger;
import java.lang.reflect.InvocationTargetException;
import javax.annotation.PostConstruct;
import javax.inject.Inject;


/***
 * Base class for data transfer related resources.
 * Dynamically selects the appropriate data transfer service, depending on the desired destination.
 */
public class DataTransferBase {

    public static final String defaultDestination = "dcache";
    public static final String HEADER_STORAGE_AUTH = "Authorization-Storage";

    public static final String DESTINATION_STORAGE = "The destination storage";
    public static final String STORAGE_AUTH = "Optional credentials for the destination storage, " +
                                              "Base-64 encoded 'user:password' or 'access-key:secret-key'";

    @Inject
    protected TransfersConfig config;

    protected Logger LOG;


    /***
     * Construct with logger
     * @param log The logger (of subclass) to use
     */
    public DataTransferBase(Logger log) {
        this.LOG = log;
    }

    /**
     * Prepare REST client for the appropriate data transfer service, based on the destination
     * configured in "proxy.transfer.destination".
     * @param params dictates which transfer service we pick, mapping is in the configuration file
     * @return true on success, updates fields "destination" and "ts"
     */
    @PostConstruct
    protected boolean getTransferService(ActionParameters params) {

        LOG.debug("Selecting transfer service...");

        if (null != params.ts)
            return true;

        if(null == params.destination || params.destination.isEmpty()) {
            LOG.error("No destination specified");
            return false;
        }

        LOG.infof("Destination is <%s>", params.destination);

        var storageConfig = config.destinations().get(params.destination);
        if (null == storageConfig) {
            // Unsupported destination
            LOG.errorf("No transfer service configured for destination <%s>", params.destination);
            return false;
        }

        var serviceConfig = config.services().get(storageConfig.serviceId());
        if (null == serviceConfig) {
            // Unsupported transfer service
            LOG.errorf("No configuration found for transfer service <%s>", storageConfig.serviceId());
            return false;
        }

        // Get the class of the transfer service we should use
        try {
            var classType = Class.forName(serviceConfig.className());
            params.ts = (TransferService)classType.getDeclaredConstructor().newInstance();
            if(params.ts.initService(serviceConfig)) {
                LOG.infof("Selected transfer service <%s>", params.ts.getServiceName());
                return true;
            }
        }
        catch (ClassNotFoundException e) {
            LOG.error(e.getMessage());
        }
        catch (NoSuchMethodException e) {
            LOG.error(e.getMessage());
        }
        catch (InstantiationException e) {
            LOG.error(e.getMessage());
        }
        catch (InvocationTargetException e) {
            LOG.error(e.getMessage());
        }
        catch (IllegalAccessException e) {
            LOG.error(e.getMessage());
        }
        catch (IllegalArgumentException e) {
            LOG.error(e.getMessage());
        }

        return false;
    }

}
