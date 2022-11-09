package parser;

import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.mutiny.ext.web.client.WebClient;
import io.smallrye.mutiny.Uni;
import org.jboss.logging.Logger;


/***
 * Class for simple web requests
 */
public class ParserHelper {

    private static final Logger LOG = Logger.getLogger(ParserHelper.class);
    private WebClient client;
    private String redirectedToUrl;


    /***
     * Constructor to wrap a Web client
     */
    public ParserHelper(WebClient client) {
        this.client = client;
    }

    public String getRedirectedToUrl() { return redirectedToUrl; }

    /***
     * Initialize the REST client for B2Share
     * @return true on success
     */
    public Uni<String> checkRedirect(String url) {

        var options = new WebClientOptions();
        options.setFollowRedirects(false);

        var result = client.headAbs(url)
            .send()
            .chain(resp -> {
                var redirects = resp.followedRedirects();
                if(!redirects.isEmpty()) {
                    // Redirected
                    this.redirectedToUrl = redirects.get(redirects.size() - 1);
                    return Uni.createFrom().item(this.redirectedToUrl);
                }

                // Not redirected
                return Uni.createFrom().nullItem();
            })
            .onFailure().invoke(e -> {
                LOG.errorf("Error in request HEAD %s", url);
            });

        return result;
    }

}
