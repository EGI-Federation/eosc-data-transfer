package eosc.eu;

import eosc.eu.model.Transfer;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import jakarta.inject.Inject;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;


/***
 * Base class for data transfer related resources.
 * Dynamically selects the appropriate data transfer service, depending on the desired destination.
 */
public class DataTransferBase {

    public static final String DEFAULT_DESTINATION = "dcache";
    public static final String HEADER_STORAGE_AUTH = "Authorization-Storage";
    public static final String DESTINATION_STORAGE = "The destination storage";
    public static final String STORAGE_AUTH = "Optional credentials for the destination storage, " +
                                              "Base-64 encoded 'user:password' or 'access-key:secret-key'";

    @Inject
    protected TransfersConfig config;

    private Logger log;


    /***
     * Construct with logger
     * @param log The logger (of subclass) to use
     */
    public DataTransferBase(Logger log) {
        this.log = log;
    }

    /**
     * Prepare REST client for the appropriate data transfer service, based on the destination
     * configured in "eosc.transfer.destination".
     * @param params dictates which transfer service we pick, mapping is in the configuration file
     * @return true on success, updates fields "destination" and "ts"
     */
    protected boolean getTransferService(ActionParameters params) {

        log.debug("Selecting transfer service");

        if (null != params.ts)
            return true;

        if(null == params.destination || params.destination.isEmpty()) {
            log.error("No destination specified");
            return false;
        }

        log.infof("Destination is %s", params.destination);

        var storageConfig = config.destinations().get(params.destination);
        if (null == storageConfig) {
            // Unsupported destination
            log.error("No transfer service configured for this destination");
            return false;
        }

        var tsID = storageConfig.serviceId();
        MDC.put("serviceId", tsID);

        var serviceConfig = config.services().get(tsID);
        if (null == serviceConfig) {
            // Unsupported transfer service
            log.error("No configuration found for transfer service");
            return false;
        }

        // Get the class of the transfer service we should use
        try {
            var classType = Class.forName(serviceConfig.className());
            params.ts = (TransferService)classType.getDeclaredConstructor().newInstance();
            if(params.ts.initService(serviceConfig)) {
                var tsName = params.ts.getServiceName();
                MDC.put("serviceName", tsName);
                log.infof("Handled by %s", tsName);
                return true;
            }
        }
        catch (ClassNotFoundException e) {
            log.error(e.getMessage());
        }
        catch (NoSuchMethodException e) {
            log.error(e.getMessage());
        }
        catch (InstantiationException e) {
            log.error(e.getMessage());
        }
        catch (InvocationTargetException e) {
            log.error(e.getMessage());
        }
        catch (IllegalAccessException e) {
            log.error(e.getMessage());
        }
        catch (IllegalArgumentException e) {
            log.error(e.getMessage());
        }

        return false;
    }

    /**
     * Embed credentials in storage element URL
     * @param destination is the type of destination storage.
     * @param seUrl is the URL to the storage element.
     * @param storageAuth contains the Base64-encoded 'username:password'
     * @return Updated URL with embedded credentials, null on error
     */
    protected String applyStorageCredentials(String destination, String seUrl, String storageAuth) {

        if(null == storageAuth || storageAuth.isBlank())
            // When no credentials, will try anonymous access
            return seUrl;

        if(destination.equalsIgnoreCase(Transfer.Destination.ftp.toString())) {
            // Add credentials to FTP URL
            URI uri = null;

            try {
                MDC.put("destinationUrl", seUrl);

                uri = new URI(seUrl);
                String protocol = uri.getScheme();
                String authority = uri.getAuthority();
                String host = uri.getHost();
                int port = uri.getPort();
                String path = uri.getPath();

                var userInfo = new DataStorageCredentials(storageAuth);
                String credentials = userInfo.isValid() ?
                        String.format("%s:%s@", userInfo.getUsername(), userInfo.getPassword()) : "";

                seUrl = String.format("%s://%s%s%s/%s",
                        protocol, credentials, host,
                        port > 0 ? (":" + port) : "",
                        path);

                if(null != uri.getQuery())
                    seUrl += ("?" + uri.getQuery());

                MDC.remove("destinationUrl");
            }
            catch(URISyntaxException e) {
                log.error("Failed to add storage credentials to destination URL");
                log.error(e.getMessage());
                return null;
            }
        }

        return seUrl;
    }

}
