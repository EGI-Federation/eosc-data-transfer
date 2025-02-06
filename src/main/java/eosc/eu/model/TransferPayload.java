package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;


/**
 * A file to transfer, includes multiple sources for the same file, and multiple destinations where to transfer it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description="Describes one file to be transferred (any source to all destinations)")
public class TransferPayload {

    @Schema(description="Multiple sources for the file to be transferred, will try them all until one is available")
    public List<String> sources;

    @Schema(description="Multiple destinations where to transfer the file")
    public List<String> destinations;

    @Schema(description="User defined checksum in the form 'algorithm:value'")
    public String checksum;


    /**
     * Constructor
     */
    public TransferPayload() {
        this.sources = new ArrayList<>();
        this.destinations = new ArrayList<>();
    }

    /**
     * Check if valid (any files to transfer, source and destination the same size)
     */
    public boolean isValid() {
        if(null == this.sources || null == this.destinations ||
           this.sources.isEmpty() || this.sources.size() != this.destinations.size())
            // Something is wrong
            return false;

        return true;
    }
}
