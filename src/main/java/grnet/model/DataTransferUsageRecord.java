package grnet.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.smallrye.config.WithName;

import java.util.Date;
import java.util.Optional;


/**
 * Usage record for a data transfer
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class DataTransferUsageRecord {

    @WithName("metric_definition_id")
    public String metricId;

    @WithName("time_period_start")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    public Date periodStart;

    @WithName("time_period_end")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    public Date periodEnd;

    @WithName("value")
    public long bytesTransferred;

    @WithName("group_id")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<String> groupId;

    @WithName("user_id")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public Optional<String> userId;


    /***
     * Constructor
     */
    public DataTransferUsageRecord() {
        this.groupId = Optional.empty();
        this.userId = Optional.empty();
    }

    /**
     * Construct from metric id and data amount
     */
    public DataTransferUsageRecord(String metricId, long bytes) {
        this.metricId = metricId;
        this.bytesTransferred = bytes;
        this.groupId = Optional.empty();
        this.userId = Optional.empty();
    }

    /**
     * Construct from metric id, data amount, and user id
     */
    public DataTransferUsageRecord(String metricId, long bytes, String voPersonId) {
        this.metricId = metricId;
        this.bytesTransferred = bytes;
        this.groupId = Optional.empty();
        this.userId = Optional.of(voPersonId);
    }
}
