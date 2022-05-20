package egi.fts.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


/**
 * Details of a transfer job
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class JobInfo {

    public String job_id;

    /**
     * Constructor
     */
    public JobInfo() {}
}
