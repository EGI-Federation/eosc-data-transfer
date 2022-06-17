package eosc.eu;

import io.smallrye.mutiny.Uni;

import eosc.eu.model.*;
import eosc.eu.ParsersConfig.ParserConfig;


/***
 * Generic parser service abstraction
 */
public interface ParserService {

    /***
     * Initialize the service, avoids the need to inject configuration.
     * @return true on success.
     */
    public abstract boolean initParser(ParserConfig config);

    /***
     * Get the human-readable name of the parser.
     * @return Name of the parser service.
     */
    public abstract String getParserName();

    /***
     * Get the Id of the source data set.
     * @return Source Id.
     */
    public abstract String getSourceId();

    /***
     * Checks if the parser service understands this DOI.
     * @param doi The DOI for a data set.
     * @return Return true if the parser service can parse this DOI.
     */
    public abstract boolean canParseDOI(String doi);

    /**
     * Parse the DOI and return a set of files in the data set.
     * @param auth The access token needed to call the service.
     * @param doi The DOI for a data set.
     * @return List of files in the data set.
     */
    public abstract Uni<StorageContent> parseDOI(String auth, String doi);
}
