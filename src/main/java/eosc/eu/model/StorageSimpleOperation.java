package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.eclipse.microprofile.openapi.annotations.media.Schema;


/**
 * Target for a file or folder operation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StorageSimpleOperation {

    @Schema(description="The URL to the storage element")
    public String seUrl;


    /**
     * Constructor
     */
    public StorageSimpleOperation() {}

    /**
     * Construct from storage element URL
     */
    public StorageSimpleOperation(String seUrl) { this.seUrl = seUrl; }

}
