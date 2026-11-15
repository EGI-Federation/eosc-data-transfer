package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.eclipse.microprofile.openapi.annotations.media.Schema;


/**
 * Targets for a file or folder rename operation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Old and new URI of a storage element to rename or move")
public class StorageRenameOperation {

    @Schema(description="The URI to the old storage element",
            example = "https://dcache.example.org/data/old.zip")
    public String seUriOld;

    @Schema(description="The URI to the new storage element",
            example = "https://dcache.example.org/data/new.zip")
    public String seUriNew;


    /**
     * Constructor
     */
    public StorageRenameOperation() {}

    /**
     * Construct from old and new storage element URLs
     * @param uriOld The URI of the element to rename
     * @param uriNew The new URI of the element
     */
    public StorageRenameOperation(String uriOld, String uriNew) {
        this.seUriOld = uriOld;
        this.seUriNew = uriNew;
    }
}
