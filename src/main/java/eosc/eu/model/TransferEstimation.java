package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;


/**
 * Estimation of a new transfer job
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Files to estimate the transfer cost of, with their sources and sizes")
public class TransferEstimation {

    @Schema(description = "One entry per file to include in the estimation")
    public List<TransferPayloadEstimation> files;
}
