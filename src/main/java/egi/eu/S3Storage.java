package egi.eu;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.tuples.Tuple2;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.RestClientDefinitionException;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;
import org.jboss.resteasy.reactive.ClientWebApplicationException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
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
import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;
import static jakarta.ws.rs.core.HttpHeaders.*;

import eosc.eu.StorageService;
import eosc.eu.StorageServiceException;
import eosc.eu.TransferConfig.StorageSystemConfig;
import eosc.eu.model.*;

import egi.s3.S3StorageService;
import egi.s3.model.*;


/***
 * Class for manipulating storage elements in S3 storage systems
 */
public class S3Storage implements StorageService {

    private static final Logger log = Logger.getLogger(S3Storage.class);

    private String name;
    private String baseUrl;
    private S3StorageService s3;
    private int timeout;

    private ObjectMapper objectMapper;


    /***
     * Constructor
     */
    public S3Storage() { this.objectMapper = new ObjectMapper(); }

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
            log.error(e);
        }
        catch (KeyStoreException e) {
            log.error(e);
        }
        catch (CertificateException e) {
            log.error(e);
        }
        catch (IOException e) {
            log.error(e);
        }
        catch (NoSuchAlgorithmException e) {
            log.error(e);
        }

        return oks;
    }

    /***
     * Initialize the REST client for the S3 storage system
     * @param serviceConfig Configuration loaded from the config file
     * @param storageElementUrl the URL to a folder or file on the target storage system
     * @return true on success
     */
    @PostConstruct
    public boolean initService(StorageSystemConfig serviceConfig, String storageElementUrl) {

        if(null != s3)
            return true;

        this.name = serviceConfig.name();
        this.timeout = serviceConfig.timeout();

        MDC.put("storageElement", storageElementUrl);
        log.debug("Obtaining REST client(s) for S3 storage");

        // Get the base URL for the S3 storage system
        URL urlStorageSystem;
        try {
            urlStorageSystem = new URL(storageElementUrl);
            this.baseUrl = urlStorageSystem.getProtocol() + "://" + urlStorageSystem.getAuthority();
            urlStorageSystem = new URL(this.baseUrl);
        } catch (MalformedURLException e) {
            log.error(e.getMessage());
            return false;
        }

        try {
            // Create the REST client for the storage system
            var rcb = RestClientBuilder.newBuilder().baseUrl(urlStorageSystem);

            s3 = rcb.build(S3StorageService.class);

            return true;
        }
        catch(IllegalStateException ise) {
            log.error(ise.getMessage());
        }
        catch (RestClientDefinitionException rcde) {
            log.error(rcde.getMessage());
        }

        return false;
    }

    /***
     * Get the human-readable name of the service.
     * @return Name of the transfer service.
     */
    public String getServiceName() { return this.name; }

    /***
     * Get the base URL of the service.
     * @return Base URL.
     */
    public String getServiceBaseUrl() { return this.baseUrl; }

    /**
     * Retrieve information about current user.
     * @param auth The access token that authorizes calling the service.
     * @return User information.
     */
    public Uni<eosc.eu.model.UserInfo> getUserInfo(String auth) {
        if(null == s3)
            return Uni.createFrom().failure(new StorageServiceException("configInvalid"));

        return Uni.createFrom().failure(new StorageServiceException("configInvalid"));
    }

    /**
     * List all files and sub-folders in a folder.
     * @param auth The access token needed to call the service.
     * @param storageAuth Optional credentials for the destination storage, Base-64 encoded "key:value"
     * @param folderUrl The link to the folder to list content of.
     * @return List of folder content.
     */
    public Uni<StorageContent> listFolderContent(String auth, String storageAuth, String folderUrl) {
        if(null == s3)
            return Uni.createFrom().failure(new StorageServiceException("configInvalid"));

        Uni<StorageContent> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new StorageServiceException("listFolderContentTimeout"))
//            .chain(unused -> {
//                // When necessary, configure S3 destination
//                if(null != storageAuth && !storageAuth.isBlank())
//                    return prepareStorages(auth, storageAuth, List.of(folderUrl));
//
//                return Uni.createFrom().item(true);
//            })
            .chain(s3ConfigResult -> {
                // List folder content
                return s3.listFolderContentAsync(auth, folderUrl);
            })
            .chain(contentList -> {
                // Got folder listing
                MDC.put("seCount", contentList.size());
                return Uni.createFrom().item(new StorageContent(folderUrl, contentList));
            })
            .onFailure().invoke(e -> {
                log.error(e);
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
        if(null == s3)
            return Uni.createFrom().failure(new StorageServiceException("configInvalid"));

        Uni<StorageElement> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new StorageServiceException("getStorageElementInfoTimeout"))
//            .chain(unused -> {
//                // When necessary, configure S3 destination
//                if(null != storageAuth && !storageAuth.isBlank())
//                    return prepareStorages(auth, storageAuth, List.of(seUrl));
//
//                return Uni.createFrom().item(true);
//            })
            .chain(s3ConfigResult -> {
                // Get object info
                return s3.getObjectInfoAsync(auth, seUrl);
            })
            .chain(objInfo -> {
                // Got object info
                objInfo.objectUrl = seUrl;
                try {
                    MDC.put("seInfo", this.objectMapper.writeValueAsString(objInfo));
                }
                catch (JsonProcessingException e) {}
                return Uni.createFrom().item(new StorageElement(objInfo));
            })
            .onFailure().invoke(e -> {
                log.error(e);
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
        if(null == s3)
            return Uni.createFrom().failure(new StorageServiceException("configInvalid"));

        Uni<String> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new StorageServiceException("createFolderTimeout"))
//            .chain(unused -> {
//                // When necessary, configure S3 destination
//                if(null != storageAuth && !storageAuth.isBlank())
//                    return prepareStorages(auth, storageAuth, List.of(folderUrl));
//
//                return Uni.createFrom().item(true);
//            })
            .chain(s3ConfigResult -> {
                // Create folder
                var operation = new ObjectOperation(folderUrl);
                return s3.createFolderAsync(auth, operation);
            })
            .chain(code -> {
                // Got success code
                return Uni.createFrom().item(code);
            })
            .onFailure().invoke(e -> {
                log.error(e);
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
        if(null == s3)
            return Uni.createFrom().failure(new StorageServiceException("configInvalid"));

        Uni<String> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new StorageServiceException("deleteFolderTimeout"))
//            .chain(unused -> {
//                // When necessary, configure S3 destination
//                if(null != storageAuth && !storageAuth.isBlank())
//                    return prepareStorages(auth, storageAuth, List.of(folderUrl));
//
//                return Uni.createFrom().item(true);
//            })
            .chain(s3ConfigResult -> {
                // Delete folder
                var operation = new ObjectOperation(folderUrl);
                return s3.deleteFolderAsync(auth, operation);
            })
            .chain(code -> {
                // Got success code
                return Uni.createFrom().item(code);
            })
            .onFailure().invoke(e -> {
                log.error(e);
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
        if(null == s3)
            return Uni.createFrom().failure(new StorageServiceException("configInvalid"));

        Uni<String> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new StorageServiceException("deleteFileTimeout"))
//            .chain(unused -> {
//                // When necessary, configure S3 destination
//                if(null != storageAuth && !storageAuth.isBlank())
//                    return prepareStorages(auth, storageAuth, List.of(fileUrl));
//
//                return Uni.createFrom().item(true);
//            })
            .chain(s3ConfigResult -> {
                // Delete file
                var operation = new ObjectOperation(fileUrl);
                return s3.deleteFileAsync(auth, operation);
            })
            .chain(code -> {
                // Got success code
                return Uni.createFrom().item(code);
            })
            .onFailure().invoke(e -> {
                log.error(e);
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
        if(null == s3)
            throw new StorageServiceException("configInvalid");

        Uni<String> result = Uni.createFrom().nullItem()

            .ifNoItem()
                .after(Duration.ofMillis(this.timeout))
                .failWith(new StorageServiceException("renameStorageElementTimeout"))
//            .chain(unused -> {
//                // When necessary, configure S3 destination
//                if(null != storageAuth && !storageAuth.isBlank())
//                    return prepareStorages(auth, storageAuth, List.of(seOld));
//
//                return Uni.createFrom().item(true);
//            })
            .chain(s3ConfigResult -> {
                // Rename storage element
                var operation = new ObjectOperation(seOld, seNew);
                return s3.renameObjectAsync(auth, operation);
            })
            .chain(code -> {
                // Got success code
                return Uni.createFrom().item(code);
            })
            .onFailure().invoke(e -> {
                log.error(e);
            });

        return result;
    }
}
