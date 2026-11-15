package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;


/**
 * A file to transfer, includes multiple sources and the size of the file.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description="Allows estimation of transfer cost for one file (any source)")
public class TransferPayloadEstimation {

    @Schema(description = "Multiple sources for the file to be transferred, will try them all until one is " +
                          "available",
            example = "https://zenodo.org/record/6511035/files/data.zip")
    public List<String> sources;

    @Schema(description = "Size of the file in bytes", example = "1073741824")
    public long size;


    /**
     * Constructor
     */
    public TransferPayloadEstimation() {
        this.sources = new ArrayList<>();
        this.size = 0;
    }

    /**
     * Check if valid (at least one source, the size provided)
     * @return true if the payload has at least one source and a positive size
     */
    public boolean isValid() {
        if(null == this.sources || this.size <= 0)
            // Something is wrong
            return false;

        return true;
    }
}
