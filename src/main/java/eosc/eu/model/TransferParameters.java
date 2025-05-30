package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.eclipse.microprofile.openapi.annotations.media.Schema;


/**
 * Parameters of a transfer job
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransferParameters {

    @JsonIgnore
    private boolean hasS3Destinations = false;

    public boolean verifyChecksum = false;
    public boolean overwrite = false;
    public int retry = 1;

    @Schema(description="Transfer priority from 1 to 5, 1 being the lowest priority")
    public int priority;

    /**
     * Constructor
     */
    public TransferParameters() {}

    public boolean hasS3Destinations() { return this.hasS3Destinations; }

    public boolean setS3Destinations(boolean hasS3) {
        this.hasS3Destinations = hasS3;
        return this.hasS3Destinations;
    }
}
