package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import cern.model.JobInfo;


/**
 * Details of a transfer job.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Identification of a newly created transfer")
public class TransferInfo {

    @Schema(description = "Type discriminator of this schema", example = "TransferInfo")
    public String kind = "TransferInfo";

    @Schema(description = "Identifier of the transfer, used with the /transfer/{jobId} endpoints",
            example = "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d")
    public String jobId;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description = "Warning returned when the job was created but could not be recorded for " +
                          "accounting")
    public String description;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @Schema(description="Cause of data transfer failure")
    public String reason;


    /**
     * Construct from FTS job info
     * @param ji The job info to construct from
     */
    public TransferInfo(JobInfo ji) {

        this.jobId = ji.job_id;
    }
}
