package egi.eu;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.tuples.Tuple2;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.RestClientDefinitionException;
import org.jboss.logging.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.Security;
import java.security.cert.CertificateException;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.PostConstruct;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import static javax.ws.rs.core.HttpHeaders.*;

import egi.fts.model.*;
import egi.fts.model.UserInfo;
import egi.fts.FileTransferService;
import eosc.eu.model.*;
import eosc.eu.TransfersConfig.TransferServiceConfig;
import eosc.eu.TransferService;
import eosc.eu.TransferServiceException;
import eosc.eu.DataStorageCredentials;
import eosc.eu.BooleanAccumulator;


/***
 * Class for working with EGI Data Transfer
 */
public class EgiDataTransfer implements TransferService {

    private static final Logger LOG = Logger.getLogger(EgiDataTransfer.class);
    private static Set<String> infoFieldsAsIs;
    private static Map<String, String> infoFieldsRenamed;

    private String name;
    private static FileTransferService fts;
    private static FileTransferService fts_s3;
    private int timeout;


    /***
     * Constructor
     */
    public EgiDataTransfer() {}

    /***
     * Load a certificate store from a resource file.
     * @param filePath File path relative to the "src/main/resource" folder
     * @param password The password for the certificate store
     * @return Loaded key store, empty optional on error
     */
    private Optional<KeyStore> loadKeyStore(String filePath, String password) {

        Optional<KeyStore> oks = Optional.empty();
        try {
            var providers = Security.getProviders();

            var classLoader = getClass().getClassLoader();
            var ksf = classLoader.getResourceAsStream(filePath);
            var ks = KeyStore.getInstance(KeyStore.getDefaultType());
            ks.load(ksf, password.toCharArray());
            oks = Optional.of(ks);
        }
        catch (FileNotFoundException e) {
            LOG.error(e);
        }
        catch (KeyStoreException e) {
            LOG.error(e);
        }
        catch (CertificateException e) {
            LOG.error(e);
        }
        catch (IOException e) {
            LOG.error(e);
        }
        catch (NoSuchAlgorithmException e) {
            LOG.error(e);
        }

        return oks;
    }

    /***
     * Initialize the REST client for the File Transfer Service that powers EGI Data Transfer
     * @return true on success
     */
    @PostConstruct
    public boolean initService(TransferServiceConfig serviceConfig) {

        this.name = serviceConfig.name();
        this.timeout = serviceConfig.timeout();

        if (null != fts)
            return true;

        LOG.debug("Obtaining REST client(s) for File Transfer Service");

        // Check if transfer service base URL is valid
        URL urlTransferService;
        try {
            urlTransferService = new URL(serviceConfig.url());
        } catch (MalformedURLException e) {
            LOG.error(e.getMessage());
            return false;
        }

        try {
            // Create the REST client for the transfer service
            var tsFile = serviceConfig.trustStoreFile().isPresent() ? serviceConfig.trustStoreFile().get() : "";
            var tsPass = serviceConfig.trustStorePassword().isPresent() ? serviceConfig.trustStorePassword().get() : "";
            var ots = loadKeyStore(tsFile, tsPass);

            var rcb = RestClientBuilder.newBuilder().baseUrl(urlTransferService);

            if(ots.isPresent())
                rcb.trustStore(ots.get());

            fts = rcb.build(FileTransferService.class);

            // If we also have a certificate for FTS (a keystore file is specified), then also create
            // another REST client that will be used to register and configure S3 storage domains
            var ksFile = serviceConfig.keyStoreFile().isPresent() ? serviceConfig.keyStoreFile().get() : "";
            var ksPass = serviceConfig.keyStorePassword().isPresent() ? serviceConfig.keyStorePassword().get() : "";
            var oks = loadKeyStore(ksFile, ksPass);

            if(oks.isPresent()) {
                rcb.keyStore(oks.get(), ksPass);

                fts_s3 = rcb.build(FileTransferService.class);
            }

            return true;
        }
        catch(IllegalStateException ise) {
            LOG.error(ise.getMessage());
        }
        catch (RestClientDefinitionException rcde) {
            LOG.error(rcde.getMessage());
        }

        return false;
    }

    /***
     * Get the human-readable name of the service.
     * @return Name of the transfer service.
     */
    public String getServiceName() { return this.name; }

    /***
     * Signal if browsing the destination is supported
     * @param destination The key of the destination storage type from the configuration file
     * @return true if creating and managing storage elements is supported in associated destination storage(s)
     */
    public boolean canBrowseStorage(String destination) {

        if(destination.equals(Transfer.Destination.dcache.toString()))
            return true;
        else if(destination.equals(Transfer.Destination.ftp.toString()))
            // TODO: Can FTS browse FTP storage systems?
            return true;
        else if(destination.equals(Transfer.Destination.s3.toString()))
            // TODO: Can FTS browse S3 storage systems?
            return true;

        return false;
    }

    /***
     * Translates name of a generic information field to the name specific to the transfer service.
     * @param genericFieldName is the name of a TransferInfoExtended field.
     * @return Name of the field specific to this transfer service, null if requested field not supported.
     */
    public String translateTransferInfoFieldName(String genericFieldName) {

        if(null == infoFieldsAsIs) {
            infoFieldsAsIs = new HashSet<>(Arrays.asList(
                    "source_se",
                    "source_space_token",
                    "priority",
                    "retry",
                    "reason",
                    "vo_name",
                    "user_dn",
                    "cred_id"));
        }

        if(infoFieldsAsIs.contains(genericFieldName)) {
            // Field supported with the same name
            return genericFieldName;
        }

        if(null == infoFieldsRenamed) {
            infoFieldsRenamed = new HashMap<>();
            infoFieldsRenamed.put("jobId", "job_id");
            infoFieldsRenamed.put("jobState", "job_state");
            infoFieldsRenamed.put("jobType", "job_type");
            infoFieldsRenamed.put("jobMetadata", "job_metadata");
            infoFieldsRenamed.put("destination_se", "dest_se");
            infoFieldsRenamed.put("destination_space_token", "space_token");
            infoFieldsRenamed.put("verifyChecksum", "verify_checksum");
            infoFieldsRenamed.put("overwrite", "overwrite_flag");
            infoFieldsRenamed.put("retryDelay", "retry_delay");
            infoFieldsRenamed.put("maxTimeInQueue", "max_time_in_queue");
            infoFieldsRenamed.put("copyPinLifetime", "copy_pin_lifetime");
            infoFieldsRenamed.put("bringOnline", "bring_online");
            infoFieldsRenamed.put("targetQOS", "target_qos");
            infoFieldsRenamed.put("cancel", "cancel_job");
            infoFieldsRenamed.put("finishedAt", "job_finished");
            infoFieldsRenamed.put("submittedAt", "submit_time");
            infoFieldsRenamed.put("submittedTo", "submit_host");
            infoFieldsRenamed.put("status", "http_status");
        }

        return infoFieldsRenamed.get(genericFieldName);
    }

    /**
     * Retrieve information about current user.
     * @param auth The access token that authorizes calling the service.
     * @return User information.
     */
    public Uni<eosc.eu.model.UserInfo> getUserInfo(String auth) {
        if(null == fts)
            return Uni.createFrom().failure(new TransferServiceException("configInvalid"));

        Uni<eosc.eu.model.UserInfo> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("getUserInfoTimeout"))
            .chain(unused -> {
                // Get user info
                return fts.getUserInfoAsync(auth);
            })
            .chain(userInfo -> {
                // Got user info
                return Uni.createFrom().item(new eosc.eu.model.UserInfo(userInfo));
            })
            .onFailure().invoke(e -> {
                LOG.error(e);
            });

        return result;
    }

    /**
     * Prepare S3 storage system for transfer.
     * @param tsAuth The access token needed to call the service.
     * @param accessKey Access key for the destination storage.
     * @param secretKey Secret key for the destination storage.
     * @param storageHost Destination S3 system.
     * @return true on success
     */
    private Uni<Boolean> prepareStorageImpl(String tsAuth, String accessKey, String secretKey, String storageHost, UserInfo ui) {

        // Make sure we have storage credentials
        if (null == accessKey || accessKey.isBlank() || null == secretKey || secretKey.isBlank())
            return Uni.createFrom().failure(new TransferServiceException("missingStorageAuth"));

        if (null == fts)
            return Uni.createFrom().failure(new TransferServiceException("configInvalid"));

        Uni<Boolean> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("prepareStorageImplTimeout"))
            .chain(unused -> {
                // Register S3 destination
                if(true)
                    // TODO: Remove this hack when FTS is fixed and registration works
                    // FTS issue https://github.com/cern-fts/fts-rest-flask/issues/4
                    return Uni.createFrom().item(new S3Info(storageHost));

                LOG.infof("Registering S3 destination %s", storageHost);

                S3Info s3info = new S3Info(storageHost);
                if(null != fts_s3)
                    return fts_s3.registerS3HostAsync("", s3info);

                return fts.registerS3HostAsync(tsAuth, s3info);
            })
            .chain(s3info -> {
                // Get user info from auth token, if not supplied
                return (null != ui) ?
                        Uni.createFrom().item(ui) :
                        fts.getUserInfoAsync(tsAuth);
            })
            .chain(userInfo -> {
                // Configure S3 destination with provided credentials
                LOG.infof("Configuring S3 destination %s", storageHost);

                S3Info s3info = new S3Info(userInfo.user_dn, accessKey, secretKey);
                if(null != fts_s3)
                    return fts_s3.configureS3HostAsync("", "s3:" + storageHost, s3info);

                return fts.configureS3HostAsync(tsAuth, "s3:" + storageHost, s3info);
            })
            .chain(unused -> {
                // Success
                return Uni.createFrom().item(true);
            })
            .onFailure().invoke(e -> {
                LOG.error(e);
                LOG.errorf("Failed to configure S3 destination %s", storageHost);
            });

        return result;
    }

    /**
     * Prepare S3 storage system for transfer.
     * @param tsAuth The access token needed to call the service.
     * @param storageAuth Credentials for the destination storage, Base-64 encoded "key:value"
     * @param seUrl Storage element URL in the destination storage system.
     * @return true on success
     */
    private Uni<Boolean> prepareStorage(String tsAuth, String storageAuth, String seUrl) {
        // Check if URL points to S3 storage system
        UserInfo userInfo = new UserInfo();
        try {
            URI uri = new URI(seUrl);
            userInfo.delegation_id = uri.getHost();
            String proto = uri.getScheme();
            if(!proto.equalsIgnoreCase(Transfer.Destination.s3.toString()))
                // Not S3 destination, nothing to do
                return Uni.createFrom().item(true);
        }
        catch(URISyntaxException e) {
            LOG.error(e.getMessage());
            LOG.errorf("Invalid destination URL %s", seUrl);
            return Uni.createFrom().failure(new TransferServiceException("urlInvalid"));
        }

        // Make sure we have storage credentials
        var storageCreds = new DataStorageCredentials(storageAuth);
        if(!storageCreds.isValid())
            return Uni.createFrom().failure(new TransferServiceException("missingStorageAuth"));

        Uni<Boolean> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("prepareStorageTimeout"))
            .chain(unused -> {
                // Get user info from auth token
                return fts.getUserInfoAsync(tsAuth);
            })
            .chain(ui -> {
                // Got user info, save DN
                String destHostname = userInfo.delegation_id;
                userInfo.delegation_id = null;
                userInfo.user_dn = ui.user_dn;

                // Prepare this storage
                return prepareStorageImpl(tsAuth, storageCreds.getAccessKey(),
                                                  storageCreds.getSecretKey(),
                                                  destHostname, userInfo);
            })
            .onFailure().invoke(e -> {
                LOG.error("Failed to configure S3 destination");
            });

        return result;
    }

    /**
     * Prepare S3 storage systems for transfer.
     * @param tsAuth The access token needed to call the service.
     * @param storageAuth Credentials for the destination storage, Base-64 encoded "key:value"
     * @param destinations List of destination S3 hostnames.
     * @return true on success
     */
    private Uni<Boolean> prepareStorages(String tsAuth, String storageAuth, List<String> destinations) {
        // Make sure we have storage credentials
        var storageCreds = new DataStorageCredentials(storageAuth);
        if(!storageCreds.isValid())
            return Uni.createFrom().failure(new TransferServiceException("missingStorageAuth"));

        UserInfo userInfo = new UserInfo();
        Uni<Boolean> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("prepareStoragesTimeout"))
            .chain(unused -> {
                // Get user info from auth token
                return fts.getUserInfoAsync(tsAuth);
            })
            .onItem().transformToMulti(ui -> {
                // Got user info, save DN
                userInfo.user_dn = ui.user_dn;
                return Multi.createFrom().iterable(destinations);
            })
            .onItem().transformToUniAndConcatenate(dest -> {
                // Prepare this storage
                return prepareStorageImpl(tsAuth, storageCreds.getAccessKey(),
                                                  storageCreds.getSecretKey(),
                                                  dest, userInfo);
            })
            .onFailure().invoke(e -> {
                LOG.error("Failed to configure S3 destinations");
            })
            .collect()
            .in(BooleanAccumulator::new, (acc, supported) -> {
                acc.accumulateAny(supported);
            })
            .onItem().transform(BooleanAccumulator::get);

        return result;
    }

    /**
     * Initiate new transfer of multiple sets of files.
     * @param auth The access token that authorizes calling the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param transfer The details of the transfer (source and destination files, parameters).
     * @return Identification for the new transfer.
     */
    public Uni<TransferInfo> startTransfer(String auth, String storageAuth, Transfer transfer) {
        if(null == fts)
            return Uni.createFrom().failure(new TransferServiceException("configInvalid"));

        Uni<TransferInfo> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("startTransferTimeout"))
            .chain(unused -> {
                // If storage authentication is provided, configure every S3 destination
                if(null != storageAuth && !storageAuth.isBlank()) {
                    // Get a list of all S3 destination storage hostnames
                    var destinations = transfer.allDestinationStorages(Transfer.Destination.s3.toString());
                    if(null == destinations)
                        // Some destination URL is invalid, abort
                        return Uni.createFrom().failure(new TransferServiceException("urlInvalid",
                                                                Tuple2.of("url", transfer.getInvalidUrl())));

                    if(!destinations.isEmpty())
                        // Configure FTS for all S3 destinations
                        return prepareStorages(auth, storageAuth, destinations);
                }

                return Uni.createFrom().item(true);
            })
            .chain(s3ConfigResult -> {
                // Start new transfer
                return fts.startTransferAsync(auth, new Job(transfer));
            })
            .chain(jobInfo -> {
                // Transfer started
                return Uni.createFrom().item(new TransferInfo(jobInfo));
            })
            .onFailure().invoke(e -> {
                LOG.error(e);
            });

        return result;
    }

    /***
     * Find transfers matching criteria.
     * @param auth The access token needed to call the service.
     * @param fields Comma separated list of fields to return for each transfer
     * @param limit Maximum number of transfers to return
     * @param timeWindow For terminal states, limit results to 'hours[:minutes]' into the past
     * @param stateIn Comma separated list of job states to match, by default returns 'ACTIVE' only
     * @param srcStorageElement Source storage element
     * @param dstStorageElement Destination storage element
     * @param delegationId Filter by delegation ID of user who started the transfer
     * @param voName Filter by VO of user who started the transfer
     * @param userDN Filter by user who started the transfer
     * @return Matching transfers.
     */
    public Uni<TransferList> findTransfers(String auth,
                                           String fields, int limit,
                                           String timeWindow, @DefaultValue("ACTIVE") String stateIn,
                                           String srcStorageElement, String dstStorageElement,
                                           String delegationId, String voName, String userDN) {
        if(null == fts)
            return Uni.createFrom().failure(new TransferServiceException("configInvalid"));

        // Translate field names
        String jobFields = null;
        if(null != fields && !fields.isEmpty()) {
            jobFields = "";
            String[] transferFields = fields.split(",");
            for (String tf : transferFields) {
                if(!jobFields.isEmpty())
                    jobFields += ",";

                String jf = this.translateTransferInfoFieldName(tf);
                if(null == jf)
                    // Found unsupported field
                    return Uni.createFrom().failure(new TransferServiceException("fieldNotSupported",
                                                                       Tuple2.of("fieldName", tf)));

                jobFields += jf;
            }
        }

        AtomicReference<String> searchFields = new AtomicReference<>(jobFields);
        Uni<TransferList> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("findTransfersTimeout"))
            .chain(unused -> {
                // List matching transfers
                return fts.findTransfersAsync(auth, searchFields.get(), limit, timeWindow, stateIn,
                                                    srcStorageElement, dstStorageElement,
                                                    delegationId, voName, userDN);
            })
            .chain(jobs -> {
                // Got matching transfers
                return Uni.createFrom().item(new TransferList(jobs));
            })
            .onFailure().invoke(e -> {
                LOG.error(e);
            });

        return result;
    }

    /**
     * Request information about a transfer.
     * @param auth The access token that authorizes calling the service.
     * @param jobId The ID of the transfer to request info about.
     * @return Details of the transfer.
     */
    public Uni<TransferInfoExtended> getTransferInfo(String auth, String jobId) {
        if(null == fts)
            return Uni.createFrom().failure(new TransferServiceException("configInvalid"));

        Uni<TransferInfoExtended> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("getTransferInfoTimeout"))
            .chain(unused -> {
                // Get transfer info
                return fts.getTransferInfoAsync(auth, jobId);
            })
            .chain(jobInfoExt -> {
                // Got transfer info
                return Uni.createFrom().item(new TransferInfoExtended(jobInfoExt));
            })
            .onFailure().invoke(e -> {
                LOG.error(e);
            });

        return result;
    }

    /**
     * Request specific field from information about a transfer.
     * @param auth The access token that authorizes calling the service.
     * @param jobId The ID of the transfer to request info about.
     * @param fieldName The name of the TransferInfoExtended field to retrieve.
     * @return The value of the requested field from a transfer's information.
     */
    public Uni<Response> getTransferInfoField(String auth, String jobId, String fieldName) {
        if(null == fts)
            return Uni.createFrom().failure(new TransferServiceException("configInvalid"));

        String jobFieldName = translateTransferInfoFieldName(fieldName);
        if(null == jobFieldName)
            return Uni.createFrom().failure(new TransferServiceException("fieldNotSupported"));

        Uni<Response> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("getTransferInfoFieldTimeout"))
            .chain(unused -> {
                // Get field value
                return fts.getTransferFieldAsync(auth, jobId, jobFieldName);
            })
            .chain(jobField -> {
                // Got field value
                Response response = Response.ok(jobField).build();

                // Determine if field is an object or a primitive type
                var entity = response.getEntity();
                String rawField = (null != entity) ? entity.toString() : "null";
                Pattern p = Pattern.compile("^\\{.+\\}$");
                Matcher m = p.matcher(rawField);
                if(!m.matches()) {
                    // Not an object, force return as text/plain
                    response = Response.ok(jobField).header(CONTENT_TYPE, MediaType.TEXT_PLAIN).build();
                }

                return Uni.createFrom().item(response);
            })
            .onFailure().invoke(e -> {
                LOG.error(e);
            });

        return result;
    }

    /**
     * Cancel a transfer.
     * @param auth The access token that authorizes calling the service.
     * @param jobId The ID of the transfer to cancel.
     * @return Details of the cancelled transfer.
     */
    public Uni<TransferInfoExtended> cancelTransfer(String auth, String jobId) {
        if(null == fts)
            return Uni.createFrom().failure(new TransferServiceException("configInvalid"));

        Uni<TransferInfoExtended> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("cancelTransferTimeout"))
            .chain(unused -> {
                // Cancel transfer
                return fts.cancelTransferAsync(auth, jobId);
            })
            .chain(jobInfoExt -> {
                // Transfer canceled, got updated transfer info
                return Uni.createFrom().item(new TransferInfoExtended(jobInfoExt));
            })
            .onFailure().invoke(e -> {
                LOG.error(e);
            });

        return result;
    }

    /**
     * List all files and sub-folders in a folder.
     * @param auth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param folderUrl The link to the folder to list content of.
     * @return List of folder content.
     */
    public Uni<StorageContent> listFolderContent(String auth, String storageAuth, String folderUrl) {
        if(null == fts)
            return Uni.createFrom().failure(new TransferServiceException("configInvalid"));

        Uni<StorageContent> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("listFolderContentTimeout"))
            .chain(unused -> {
                // When necessary, configure S3 destination
                if(null != storageAuth && !storageAuth.isBlank())
                    return prepareStorage(auth, storageAuth, folderUrl);

                return Uni.createFrom().item(true);
            })
            .chain(s3ConfigResult -> {
                // List folder content
                return fts.listFolderContentAsync(auth, folderUrl);
            })
            .chain(contentList -> {
                // Got folder listing
                return Uni.createFrom().item(new StorageContent(folderUrl, contentList));
            })
            .onFailure().invoke(e -> {
                LOG.error(e);
            });

        return result;
    }

    /**
     * Get the details of a file or folder.
     * @param auth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param seUrl The link to the file or folder to det details of.
     * @return Details about the storage element.
     */
    public Uni<StorageElement> getStorageElementInfo(String auth, String storageAuth, String seUrl) {
        if(null == fts)
            return Uni.createFrom().failure(new TransferServiceException("configInvalid"));

        Uni<StorageElement> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("getStorageElementInfoTimeout"))
            .chain(unused -> {
                // When necessary, configure S3 destination
                if(null != storageAuth && !storageAuth.isBlank())
                    return prepareStorage(auth, storageAuth, seUrl);

                return Uni.createFrom().item(true);
            })
            .chain(s3ConfigResult -> {
                // Get object info
                return fts.getObjectInfoAsync(auth, seUrl);
            })
            .chain(objInfo -> {
                // Got object info
                objInfo.objectUrl = seUrl;
                return Uni.createFrom().item(new StorageElement(objInfo));
            })
            .onFailure().invoke(e -> {
                LOG.error(e);
            });

        return result;
    }

    /**
     * Create new folder.
     * @param auth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param folderUrl The link to the folder to create.
     * @return Confirmation message.
     */
    public Uni<String> createFolder(String auth, String storageAuth, String folderUrl) {
        if(null == fts)
            return Uni.createFrom().failure(new TransferServiceException("configInvalid"));

        Uni<String> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("createFolderTimeout"))
            .chain(unused -> {
                // When necessary, configure S3 destination
                if(null != storageAuth && !storageAuth.isBlank())
                    return prepareStorage(auth, storageAuth, folderUrl);

                return Uni.createFrom().item(true);
            })
            .chain(s3ConfigResult -> {
                // Create folder
                var operation = new ObjectOperation(folderUrl);
                return fts.createFolderAsync(auth, operation);
            })
            .chain(code -> {
                // Got success code
                return Uni.createFrom().item(code);
            })
            .onFailure().invoke(e -> {
                LOG.error(e);
            });

        return result;
    }

    /**
     * Delete existing folder.
     * @param auth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param folderUrl The link to the folder to delete.
     * @return Confirmation message.
     */
    public Uni<String> deleteFolder(String auth, String storageAuth, String folderUrl) {
        if(null == fts)
            return Uni.createFrom().failure(new TransferServiceException("configInvalid"));

        Uni<String> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("deleteFolderTimeout"))
            .chain(unused -> {
                // When necessary, configure S3 destination
                if(null != storageAuth && !storageAuth.isBlank())
                    return prepareStorage(auth, storageAuth, folderUrl);

                return Uni.createFrom().item(true);
            })
            .chain(s3ConfigResult -> {
                // Delete folder
                var operation = new ObjectOperation(folderUrl);
                return fts.deleteFolderAsync(auth, operation);
            })
            .chain(code -> {
                // Got success code
                return Uni.createFrom().item(code);
            })
            .onFailure().invoke(e -> {
                LOG.error(e);
            });

        return result;
    }

    /**
     * Delete existing file.
     * @param auth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param fileUrl The link to the file to delete.
     * @return Confirmation message.
     */
    public Uni<String> deleteFile(String auth, String storageAuth, String fileUrl) {
        if(null == fts)
            return Uni.createFrom().failure(new TransferServiceException("configInvalid"));

        Uni<String> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("deleteFileTimeout"))
            .chain(unused -> {
                // When necessary, configure S3 destination
                if(null != storageAuth && !storageAuth.isBlank())
                    return prepareStorage(auth, storageAuth, fileUrl);

                return Uni.createFrom().item(true);
            })
            .chain(s3ConfigResult -> {
                // Delete file
                var operation = new ObjectOperation(fileUrl);
                return fts.deleteFileAsync(auth, operation);
            })
            .chain(code -> {
                // Got success code
                return Uni.createFrom().item(code);
            })
            .onFailure().invoke(e -> {
                LOG.error(e);
            });

        return result;
    }

    /**
     * Rename a folder or file.
     * @param auth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param seOld The link to the storage element to rename.
     * @param seNew The link to the new name/location of the storage element.
     * @return Confirmation message.
     */
    public Uni<String> renameStorageElement(String auth, String storageAuth, String seOld, String seNew) {
        if(null == fts)
            throw new TransferServiceException("configInvalid");

        Uni<String> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new TransferServiceException("renameStorageElementTimeout"))
            .chain(unused -> {
                // When necessary, configure S3 destination
                if(null != storageAuth && !storageAuth.isBlank())
                    return prepareStorage(auth, storageAuth, seOld);

                return Uni.createFrom().item(true);
            })
            .chain(s3ConfigResult -> {
                // Rename storage element
                var operation = new ObjectOperation(seOld, seNew);
                return fts.renameObjectAsync(auth, operation);
            })
            .chain(code -> {
                // Got success code
                return Uni.createFrom().item(code);
            })
            .onFailure().invoke(e -> {
                LOG.error(e);
            });

        return result;
    }
}
