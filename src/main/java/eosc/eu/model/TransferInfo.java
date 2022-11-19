package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import egi.fts.model.JobInfo;


/**
 * Details of a transfer job.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransferInfo {

    public String kind = "TransferInfo";
    public String jobId;


    /**
     * Construct from FTS job info
     */
    public TransferInfo(JobInfo ji) {

        this.jobId = ji.job_id;
    }
}
