package eosc.eu.model;

import egi.fts.model.JobInfo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


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
