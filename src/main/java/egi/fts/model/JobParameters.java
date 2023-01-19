package egi.fts.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import eosc.eu.model.TransferParameters;

import java.util.Optional;


/**
 * Parameters of a transfer job
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobParameters {

    public boolean overwrite = false;
    public int retry = 1;
    public boolean verify_checksum = false;

    @Schema(title="Transfer priority from 1 to 5, 1 being the lowest priority")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public int priority = 0;

    @Schema(title="Disable all checks, just copy the file")
    public boolean strict_copy = false;

    @Schema(title="Force IPv4 if the underlying protocol supports it")
    public boolean ipv4 = false;

    @Schema(title="Force IPv6 if the underlying protocol supports it")
    public boolean ipv6 = false;

    @Schema(title="Set to true when transferring to S3 destinations")
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Optional<Boolean> s3alternate;


    /**
     * Constructor
     */
    public JobParameters() {}

    /**
     * Construct from generic transfer parameters
     */
    public JobParameters(TransferParameters params) {
        this.overwrite = params.overwrite;
        this.retry = params.retry;
        this.verify_checksum = params.verifyChecksum;
        this.priority = params.priority;
        this.strict_copy = params.strictCopy;
        this.ipv4 = params.ipv4;
        this.ipv6 = params.ipv6;

        if(params.hasS3Destinations())
            this.s3alternate = Optional.of(true);
    }
}
