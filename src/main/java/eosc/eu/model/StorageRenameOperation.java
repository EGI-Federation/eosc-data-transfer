package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.eclipse.microprofile.openapi.annotations.media.Schema;


/**
 * Targets for a file or folder rename operation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StorageRenameOperation {

    @Schema(title="The URL to the old storage element")
    public String seUrlOld;

    @Schema(title="The URL to the new storage element")
    public String seUrlNew;


    /**
     * Constructor
     */
    public StorageRenameOperation() {}

    /**
     * Construct from old and new storage element URLs
     */
    public StorageRenameOperation(String urlOld, String urlNew) {
        this.seUrlOld = urlOld;
        this.seUrlNew = urlNew;
    }
}
