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

    @Schema(description="Multiple sources for the file to be transferred, will try them all until one is available")
    public List<String> sources;

    @Schema(description="The size of the file'")
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
     */
    public boolean isValid() {
        if(null == this.sources || this.size <= 0)
            // Something is wrong
            return false;

        return true;
    }
}
