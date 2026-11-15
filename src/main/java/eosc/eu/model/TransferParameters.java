package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.eclipse.microprofile.openapi.annotations.media.Schema;


/**
 * Parameters of a transfer job
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Job-wide parameters of a transfer; omitted values use the engine defaults")
public class TransferParameters {

    @JsonIgnore
    private boolean hasS3Destinations = false;

    @Schema(description = "Verify the checksum of every file after transfer, when the storage supports it",
            defaultValue = "false")
    public boolean verifyChecksum = false;

    @Schema(description = "Overwrite destination files that already exist", defaultValue = "false")
    public boolean overwrite = false;

    @Schema(description = "Number of retry attempts per file", defaultValue = "1", minimum = "0")
    public int retry = 1;

    @Schema(description = "Transfer priority from 1 (lowest) to 5 (highest); 0 lets the engine choose",
            defaultValue = "0", minimum = "0", maximum = "5")
    public int priority;

    /**
     * Constructor
     */
    public TransferParameters() {}

    /**
     * Whether any destination in this transfer uses S3
     * @return true if at least one destination URI uses the s3 or s3s protocol
     */
    public boolean hasS3Destinations() { return this.hasS3Destinations; }

    /**
     * Record whether any destination in this transfer uses S3
     * @param hasS3 true if at least one destination URI uses the s3 or s3s protocol
     * @return the updated value of the flag
     */
    public boolean setS3Destinations(boolean hasS3) {
        this.hasS3Destinations = hasS3;
        return this.hasS3Destinations;
    }
}
