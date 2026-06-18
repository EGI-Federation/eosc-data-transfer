package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;


/**
 * Estimation of a new transfer job
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransferEstimation {

    @Schema(description="The files to be transferred")
    public List<TransferPayloadEstimation> files;
}
