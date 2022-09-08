package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import egi.fts.model.JobInfoExtended;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;


/**
 * Extended details of a transfer job.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransferInfoExtended extends TransferInfo {

    // NOTE: When adding/renaming fields, also update all translateTransferInfoFieldName() methods

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public TransferStatus jobState;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String jobType;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Map<String, String> jobMetadata;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String source_se;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String source_space_token;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String destination_se;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String destination_space_token;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String verifyChecksum;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<Boolean> overwrite;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<Integer> priority;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<Integer> retry;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<Integer> retryDelay;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<Integer> maxTimeInQueue;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<Integer> copyPinLifetime;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<Integer> bringOnline;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<Integer> targetQOS;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<Boolean> cancel;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public Date submittedAt;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String submittedTo;

    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    public Date finishedAt;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String status; // "200 OK"

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String reason;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String vo_name;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String user_dn;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String cred_id;


    /**
     * Construct from extended FTS job info
     */
    public TransferInfoExtended(JobInfoExtended jie) {

        super(jie);

        this.kind = "TransferInfoExtended";
        this.jobId = jie.job_id;
        this.jobState = TransferStatus.fromString(jie.job_state);
        this.jobType = jie.job_type;

        this.jobMetadata = new HashMap<>();
        if(null != jie.job_metadata && !jie.job_metadata.isEmpty())
            this.jobMetadata.putAll(jie.job_metadata);

        this.source_se = jie.source_se;
        this.source_space_token = jie.source_space_token;
        this.destination_se = jie.destination_se;
        this.destination_space_token = jie.space_token;

        this.verifyChecksum = jie.verify_checksum;
        this.overwrite = jie.overwrite_flag;
        this.priority = jie.priority;
        this.retry = jie.retry;
        this.retryDelay = jie.retry_delay;
        this.maxTimeInQueue = jie.max_time_in_queue;
        this.copyPinLifetime = jie.copy_pin_lifetime;
        this.bringOnline = jie.bring_online;
        this.targetQOS = jie.target_qos;
        this.cancel = jie.cancel_job;

        this.submittedAt = jie.submit_time;
        this.submittedTo = jie.submit_host;
        this.finishedAt = jie.job_finished;
        this.status = jie.http_status;
        this.reason = jie.reason;

        this.vo_name = jie.vo_name;
        this.user_dn = jie.user_dn;
        this.cred_id = jie.cred_id;
    }


    /***
     * The status of a transfer
     */
    public enum TransferStatus
    {
        unused("unused"),
        active("active"),
        succeeded("succeeded"),
        failed("failed"),
        canceled("canceled");

        private String status;

        TransferStatus(String status) { this.status = status; }

        public static TransferStatus fromString(String status_) {
            final String status = status_.toLowerCase();
            if(status.contains("succes"))
                return succeeded;
            else if(status.contains("fail"))
                return failed;
            else if(status.contains("cancel"))
                return canceled;
            else if(status.contains("active") || status.contains("progress"))
                return active;

            return unused;
        }

        public String getStatus() { return this.status; }
    }
}
