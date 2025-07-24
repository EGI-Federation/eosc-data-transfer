package eosc.eu;

import eosc.eu.model.Transfer;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

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
    public static final String DESTINATION_STORAGE = "The destination of a transfer";
    public static final String STORAGE_AUTH = "Optional credentials for the destination storage, " +
                                              "Base-64 encoded 'user:password' or 'access-key:secret-key'";

    private final Logger log;

    @Inject
    protected TransferConfig config;


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

        if (null == params.destination || params.destination.isEmpty()) {
            log.error("No destination specified");
            return false;
        }

        log.infof("Destination is %s", params.destination);

        var destinationConfig = config.destinations().get(params.destination);
        if (null == destinationConfig) {
            // Unsupported destination
            log.error("No configuration for this destination");
            return false;
        }

        var tsID = destinationConfig.serviceId();
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
                log.infof("Transfers handled by %s", tsName);
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
     * Prepare REST client for the appropriate data storage system, based on the destination
     * configured under "eosc.transfer.destination".
     * @param params dictates which destination we pick, which in turn says what storage system
     *               is at that destination - mapping is in the configuration file
     * @param storageElementUrl is the fully qualified URL to a storage element (file or folder), which
     *                          is used to create a REST client for this particular storage system, or
     *                          null to not attempt creation of a REST client
     * @return true on success, updates fields "destination" and "ss"
     */
    protected boolean getStorageSystem(ActionParameters params, String storageElementUrl) {

        log.debug("Selecting storage system");

        if (null != params.ss)
            return true;

        if (null == params.destination || params.destination.isEmpty()) {
            log.error("No destination specified");
            return false;
        }

        log.infof("Destination is %s", params.destination);

        var destinationConfig = config.destinations().get(params.destination);
        if (null == destinationConfig) {
            // Unsupported destination
            log.error("No configuration for this destination");
            return false;
        }

        var ssID = destinationConfig.storageId().isEmpty() ? null : destinationConfig.storageId().get();
        if (null == ssID || ssID.isBlank()) {
            // Manipulation of storage elements not supported in this destination
            log.error("Storage element manipulation not supported in this destination");
            return false;
        }

        MDC.put("storageId", ssID);

        var storageConfig = config.storages().get(ssID);
        if (null == storageConfig) {
            // Unsupported storage system
            log.error("No configuration found for storage system");
            return false;
        }

        try {
            // Get the class of the storage system we should use and instantiate it
            var classType = Class.forName(storageConfig.className());
            params.ss = (StorageService) classType.getDeclaredConstructor().newInstance();

            // If we got a URL to a storage element, also create a REST client pointed to that particular storage system
            if(null != storageElementUrl && params.ss.initService(storageConfig, storageElementUrl)) {
                var ssName = params.ss.getServiceName();
                MDC.put("storageName", ssName);
                log.infof("Storage elements handled by %s", ssName);
            }

            return true;
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
