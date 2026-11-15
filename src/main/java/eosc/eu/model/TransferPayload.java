package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.ArrayList;
import java.util.List;


/**
 * A file to transfer, includes multiple sources for the same file, and multiple destinations where to transfer it.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description="Describes one file to be transferred (any source to any destination)")
public class TransferPayload {

    @Schema(description = "Multiple sources for the file to be transferred, will try them all until one is " +
                          "available",
            example = "https://zenodo.org/record/6511035/files/data.zip")
    public List<String> sources;

    @Schema(description = "Multiple destinations where to transfer the file, will try them all until one " +
                          "succeeds. The protocol of the URI selects the storage system",
            example = "https://dcache.example.org/data/data.zip")
    public List<String> destinations;

    @Schema(description = "User defined checksum in the form 'algorithm:value'", example = "adler32:12345678")
    public String checksum;


    /**
     * Constructor
     */
    public TransferPayload() {
        this.sources = new ArrayList<>();
        this.destinations = new ArrayList<>();
    }

    /**
     * Check if valid (at least one source and one destination)
     * @return true if the payload has at least one source and one destination
     */
    public boolean isValid() {
        if(null == this.sources || null == this.destinations ||
           this.sources.isEmpty() || this.destinations.isEmpty())
            // Something is wrong
            return false;

        return true;
    }
}
