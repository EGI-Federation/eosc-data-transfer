package eosc.eu;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.tuples.Tuple2;

import eosc.eu.model.*;
import eosc.eu.ParsersConfig.ParserConfig;
import parser.ParserHelper;


/***
 * Generic parser service abstraction
 */
public interface ParserService {

    /***
     * Initialize the service, avoids the need to inject configuration.
     * @param config Configuration loaded from the config file.
     * @return true on success.
     */
    boolean init(ParserConfig config);

    /***
     * Get the Id of the parser.
     * @return Id of the parser service.
     */
    String getId();

    /***
     * Get the human-readable name of the parser.
     * @return Name of the parser service.
     */
    String getName();

    /***
     * Get the Id of the source data set (aka record).
     * @return Source Id.
     */
    String sourceId();

    /***
     * Checks if the parser service understands this DOI.
     * @param auth The access token needed to call the service.
     * @param doi The DOI for a data set.
     * @return Return true if the parser service can parse this DOI.
     */
    Uni<Tuple2<Boolean, ParserService>> canParseDOI(String auth, String doi, ParserHelper helper);

    /**
     * Parse the DOI and return a set of files in the data set.
     * @param auth The access token needed to call the service.
     * @param doi The DOI for a data set.
     * @return List of files in the data set.
     */
    Uni<StorageContent> parseDOI(String auth, String doi);
}
