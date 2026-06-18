package eosc.eu;

import io.smallrye.mutiny.Uni;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;

import jakarta.inject.Inject;

import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;

import eosc.eu.model.Transfer;


/***
 * Base class for data transfer related resources.
 * Dynamically selects the appropriate data transfer service, depending on the desired destination.
 */
public class DataTransferBase {

    public static final String DEFAULT_DESTINATION = "dcache";
    public static final String DEFAULT_FILE_INFO = "none";
    public static final String HEADER_STORAGE_AUTH = "Authorization-Storage";
    public static final String DESTINATION_STORAGE = "The destination of the transfer";
    public static final String FILE_INFO_FOR = "Selects for which files to return transfer status";
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
     * Get configuration for a destination, configured in "eosc.transfer.destination".
     * @param config is a configuration object mapping `eosc.transfer`
     * @param destination dictates which transfer service we pick, mapping is in the configuration file
     * @param log is the logger to use
     * @return Destination configuration, null on error
     */
    public static TransferConfig.DestinationConfig getDestinationConfig(TransferConfig config, String destination,
                                                                        Logger log) {

        if(null == destination || destination.isEmpty()) {
            log.error("No destination specified");
            return null;
        }

        MDC.put("destination", destination);

        var destinationConfig = config.destinations().get(destination);
        if(null == destinationConfig) {
            // Unsupported destination
            log.errorf("No configuration found for destination <%s>", destination);
            return null;
        }

        return destinationConfig;
    }
    
    /**
     * Prepare REST client for the appropriate data transfer engine, based on the destination
     * Mapping is in the configuration file under `eosc.transfer.destination`
     * @param config is a configuration object mapping `eosc.transfer`
     * @param tsID is the ID of the transfer engine to use
     * @param log is the logger to use
     * @param logInit is true to log the success of the initializing the picked transfer engine
     * @return an initialized TransferService for the specified destination, or null on error
     */
    public static TransferService getTransferService(TransferConfig config, String tsID,
                                                     Logger log, boolean logInit) {

        MDC.put("serviceId", tsID);

        var serviceConfig = config.services().get(tsID);
        if(null == serviceConfig) {
            // Unsupported transfer service
            log.errorf("No configuration found for transfer service <%s>", tsID);
            return null;
        }

        // Get the class of the transfer service we should use
        TransferService ts = null;
        try {
            var classType = Class.forName(serviceConfig.className());
            ts = (TransferService)classType.getDeclaredConstructor().newInstance();
            if(ts.initService(serviceConfig)) {
                if(logInit) {
                    var tsName = ts.getServiceName();
                    MDC.put("serviceName", tsName);
                    log.infof("Transfer handled by %s", tsName);
                }
            }
            else {
                // Init failed, cleanup
                ts = null;
            }
        }
        catch(ClassNotFoundException | NoSuchMethodException | InstantiationException |
               InvocationTargetException | IllegalAccessException | IllegalArgumentException e) {
            log.error(e.getMessage());
        }

        return ts;
    }

    /**
     * Prepare REST client for the appropriate data transfer service, based on the destination
     * configured in "eosc.transfer.destination".
     * @param destination dictates which transfer service we pick, mapping is in the configuration file
     * @return ActionParameters instance on success, with fields "destination" and "ts" filled in
     */
    protected Uni<ActionParameters> getTransferService(String destination) {

        log.debug("Selecting transfer service");

        Uni<ActionParameters> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Pick transfer service and create REST client for it
                var destinationConfig = getDestinationConfig(config, destination, log);
                if(null == destinationConfig)
                    // No or unsupported destination
                    return Uni.createFrom().failure(new TransferServiceException("destInvalid"));

                var params = new ActionParameters(destination);
                params.ts = getTransferService(config, destinationConfig.serviceId(), log, true);
                if(null == params.ts)
                    // Could not get REST client
                    return Uni.createFrom().failure(new TransferServiceException("configInvalid"));

                return Uni.createFrom().item(params);
            });

        return result;
    }

    /**
     * Prepare REST client for the appropriate data storage system, based on the destination
     * configured under "eosc.transfer.destination".
     * @param params contains the destination, which allows checking what storage system is at that
     *               destination, mapping is in the configuration file
     * @param storageElementUrl is the fully qualified URL to a storage element (file or folder), which
     *                          is used to create a REST client for this particular storage system, or
     *                          null to not attempt creation of a REST client
     * @param auth Optional credentials for the storage system (if it uses access tokens)
     * @param storageAuth Optional credentials for the storage system, Base-64 encoded "key:value"
     * @return ActionParameters instance on success, with fields "destination" and "ss" filled in
     */
    protected Uni<ActionParameters> getStorageSystem(ActionParameters params, String storageElementUrl,
                                       String auth, String storageAuth) {

        log.debug("Selecting storage system");

        Uni<ActionParameters> result = Uni.createFrom().nullItem()

            .chain(unused -> {
                // Get configuration for the specified destination
                var destinationConfig = getDestinationConfig(config, params.destination, log);
                if(null == destinationConfig)
                    // No or unsupported destination
                    return Uni.createFrom().failure(new TransferServiceException("destInvalid"));

                var ssID = destinationConfig.storageId().isEmpty() ? null : destinationConfig.storageId().get();
                if(null == ssID || ssID.isBlank()) {
                    // Manipulation of storage elements not supported in this destination
                    log.error("Storage element manipulation not supported in this destination");
                    return Uni.createFrom().nullItem();
                }

                MDC.put("storageId", ssID);

                // Get configuration of the storage system configured for the specified destination
                var storageConfig = config.storages().get(ssID);
                if(null == storageConfig) {
                    // Unsupported storage system
                    log.error("No configuration found for storage system");
                    return Uni.createFrom().failure(new TransferServiceException("configInvalid"));
                }

                return Uni.createFrom().item(storageConfig);
            })
            .chain(storageConfig -> {
                if(null != storageConfig) {
                    try {
                        // Get the class of the storage system we should use and instantiate it
                        var classType = Class.forName(storageConfig.className());
                        params.ss = (StorageService) classType.getDeclaredConstructor().newInstance();

                        // If we got a URL to a storage element, create a client for that particular storage system
                        if(null != storageElementUrl) {
                            var authType = storageConfig.authType();
                            var authorization = (null != authType &&
                                    authType.equalsIgnoreCase(Transfer.AuthorizeWith.keys.toString())) ?
                                    storageAuth : auth;

                            if(params.ss.initService(storageConfig, storageElementUrl, authorization)) {
                                var ssName = params.ss.getServiceName();
                                MDC.put("storageName", ssName);
                                log.infof("Storage elements handled by %s", ssName);
                            } else
                                return Uni.createFrom().failure(new TransferServiceException("configInvalid"));
                        }
                    } catch(ClassNotFoundException | NoSuchMethodException | InstantiationException |
                            InvocationTargetException | IllegalAccessException | IllegalArgumentException e) {
                        log.error(e.getMessage());
                        return Uni.createFrom().failure(new TransferServiceException("configInvalid"));
                    }
                }

                return Uni.createFrom().item(params);
            });

        return result;
    }

    /**
     * Prepare REST client for the appropriate data storage system, based on the destination
     * configured under "eosc.transfer.destination".
     * @param destination allows checking what storage system is at that destination,
     *                    mapping is in the configuration file
     * @param storageElementUrl is the fully qualified URL to a storage element (file or folder), which
     *                          is used to create a REST client for this particular storage system, or
     *                          null to not attempt creation of a REST client
     * @param auth Optional credentials for the storage system (if it uses access tokens)
     * @param storageAuth Optional credentials for the storage system, Base-64 encoded "key:value"
     * @return ActionParameters instance on success, with fields "destination" and "ss" filled in, but "ss"
     *         will be null if storage element manipulation is not supported on the indicated destination
     */
    protected Uni<ActionParameters> getStorageSystem(String destination, String storageElementUrl,
                                                     String auth, String storageAuth) {

        var params = new ActionParameters(destination);
        return getStorageSystem(params, storageElementUrl, auth, storageAuth);
    }

    /**
     * Embed credentials in storage element URL
     * @param destination is the type of destination storage.
     * @param seUri is the URI to the storage element.
     * @param storageAuth contains the Base64-encoded 'username:password'
     * @return Updated URL with embedded credentials, null on error
     */
    protected String applyStorageCredentials(String destination, String seUri, String storageAuth) {

        if(null == storageAuth || storageAuth.isBlank())
            // When no credentials, will try anonymous access
            return seUri;

        if(destination.equalsIgnoreCase(Transfer.Destination.ftp.toString())) {
            // Add credentials to FTP URLs
            URI uri = null;

            try {
                MDC.put("destinationUri", seUri);

                uri = new URI(seUri);
                String protocol = uri.getScheme();
                String authority = uri.getAuthority();
                String host = uri.getHost();
                int port = uri.getPort();
                String path = uri.getPath();

                var userInfo = new DataStorageCredentials(storageAuth);
                String credentials = userInfo.isValid() ?
                        String.format("%s:%s@", userInfo.getUsername(), userInfo.getPassword()) : "";

                seUri = String.format("%s://%s%s/%s", protocol, credentials, authority, path);

                if(null != uri.getQuery())
                    seUri += ("?" + uri.getQuery());

                MDC.remove("destinationUri");
            }
            catch(URISyntaxException e) {
                log.error("Failed to add storage credentials to destination URL");
                log.error(e.getMessage());
                return null;
            }
        }

        return seUri;
    }

}
