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
    public int count;

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public List<StorageElement> elements;


    /**
     * Constructor
     */
    public StorageContent() {
        this.count = 0;
        this.elements = new ArrayList<>();
    }

    /**
     * Copy constructor makes deep copy
     */
    public StorageContent(StorageContent storage) {
        this.count = storage.elements.size();
        this.elements = new ArrayList<>(this.count);
        this.elements.addAll(storage.elements);
    }
}
