package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;


/**
 * Base properties of a storage element (file or folder)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StorageElementBase {

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String kind;
    public String name;


    /**
     * Constructor
     */
    public StorageElementBase() {}

    /**
     * Construct but change kind
     */
    public StorageElementBase(String kind) {
        this.kind = kind;
    }
}
