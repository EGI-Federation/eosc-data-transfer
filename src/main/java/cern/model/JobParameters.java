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

    @Schema(title="If set to true, the transfer will be a multihop transfer")
    public boolean multihop = false;

    @Schema(title="Number of retries")
    public int retry = 1;

    @Schema(title="Transfer priority from 1 to 5, 1 being the lowest priority")
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public int priority = 0;


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
        this.priority = params.priority;
    }
}
