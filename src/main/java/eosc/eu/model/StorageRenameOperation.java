package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.eclipse.microprofile.openapi.annotations.media.Schema;


/**
 * Targets for a file or folder rename operation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StorageRenameOperation {

    @Schema(description="The URI to the old storage element")
    public String seUriOld;

    @Schema(description="The URI to the new storage element")
    public String seUriNew;


    /**
     * Constructor
     */
    public StorageRenameOperation() {}

    /**
     * Construct from old and new storage element URLs
     */
    public StorageRenameOperation(String uriOld, String uriNew) {
        this.seUriOld = uriOld;
        this.seUriNew = uriNew;
    }
}
