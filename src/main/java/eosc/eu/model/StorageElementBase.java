package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;


/**
 * Base properties of a storage element (file or folder)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StorageElementBase {

    public String kind;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public String name;


    /**
     * Constructor
     */
    public StorageElementBase(String kind, String name) {
        this.kind = kind;
        this.name = name;
    }

    /**
     * Construct but change kind
     */
    public StorageElementBase(String kind) {
        this.kind = kind;
    }
}
