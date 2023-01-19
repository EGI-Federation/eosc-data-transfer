package egi.fts.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;


/**
 * Details of an S3 storage system
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class S3Info {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String storage_name;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String user_dn;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String vo_name;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String access_key;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public String secret_key;


    /***
     * Constructor
     */
    public S3Info() {}

    /**
     * Construct from hostname
     */
    public S3Info(String host) {
        this.storage_name = "s3:" + host.toLowerCase();
    }

    /**
     * Construct from user DN and keys
     */
    public S3Info(String userDN, String accessKey, String secretKey) {
        this.user_dn = userDN;
        this.access_key = accessKey;
        this.secret_key = secretKey;
    }
}
