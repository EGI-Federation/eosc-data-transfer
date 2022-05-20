package eosc.eu.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.List;


/**
 * Content of a storage
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class StorageContent {

    public String kind = "StorageContent";
    public long count;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<StorageElement> elements;


    /**
     * Constructor
     */
    public StorageContent() {
        this.elements = new ArrayList<>();
    }

    /**
     * Copy constructor makes deep copy
     */
    public StorageContent(StorageContent storage) {
        if(null == this.elements)
            this.elements = new ArrayList<>();
        else
            this.elements.clear();

        this.elements.addAll(storage.elements);
        this.count = this.elements.size();
    }
}
