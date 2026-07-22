package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;
import java.util.Optional;


/**
 * Estimation of a new transfer job
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransferEstimation {

    @Schema(description="The files to be transferred")
    public List<TransferPayloadEstimation> files;

    @WithName("bytes-per-credit")
    @WithDefault("-1")
    public Optional<Long> bytesPerCredit;


    /***
     * Constructor
     */
    public TransferEstimation() {
        this.bytesPerCredit = Optional.empty();
    }
}
