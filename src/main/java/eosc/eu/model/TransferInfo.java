package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import cern.model.JobInfo;


/**
 * Details of a transfer job.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransferInfo {

    public String kind = "TransferInfo";
    public String jobId;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String description;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description="Cause of data transfer failure")
    public String reason;


    /**
     * Construct from FTS job info
     */
    public TransferInfo(JobInfo ji) {

        this.jobId = ji.job_id;
    }
}
