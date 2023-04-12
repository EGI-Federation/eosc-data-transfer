package eosc.eu;

import eosc.eu.model.Transfer;
import org.jboss.logging.Logger;

import javax.inject.Inject;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;


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
     * configured in "eosc.transfer.destination".
     * @param params dictates which transfer service we pick, mapping is in the configuration file
     * @return true on success, updates fields "destination" and "ts"
     */
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
            }
            catch(URISyntaxException e) {
                LOG.errorf("Invalid destination URL %s", seUrl);
                LOG.error(e.getMessage());
                return null;
            }
        }

        return seUrl;
    }

}
