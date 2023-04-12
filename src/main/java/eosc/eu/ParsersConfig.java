package eosc.eu;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.Map;
import java.util.Optional;


/***
 * The configuration of the parsers
 */
@ConfigMapping(prefix = "eosc")
public interface ParsersConfig {

    // Contains the details of each specific parser
    @WithName("parser")
    Map<String, ParserConfig> parsers();


    /***
     * The configuration of a parser
     */
    interface ParserConfig {
        String name();

        Optional<String> url();

        @WithDefault("5000")
        int timeout(); // milliseconds

        @WithName("class")
        String className();
    }
}
