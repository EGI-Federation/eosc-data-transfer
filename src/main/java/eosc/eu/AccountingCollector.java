package eosc.eu;

import io.quarkus.oidc.client.OidcClient;
import io.quarkus.oidc.client.runtime.TokensHelper;
import io.quarkus.oidc.common.runtime.OidcConstants;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.RestClientDefinitionException;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.Cancellable;
import io.quarkus.redis.datasource.stream.StreamMessage;
import io.quarkus.redis.datasource.stream.XReadGroupArgs;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.stream.ReactiveStreamCommands;
import io.quarkus.runtime.Startup;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Inject;

import java.net.MalformedURLException;
import java.net.URL;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Collections.emptyList;
import static eosc.eu.Utils.loadKeyStore;

import eosc.eu.model.TransferPayloadInfo.FileDetails;
import eosc.eu.model.TransferInfoExtended.TransferState;
import eosc.eu.model.TransferPayloadInfo.FileState;
import grnet.AccountingService;
import grnet.model.DataTransferUsageRecord;


@Startup
@ApplicationScoped
public class AccountingCollector {

    private final Logger log = Logger.getLogger(AccountingCollector.class);

    public static final String CHANNEL = "transfer";
    public static final String STREAM = "jobs";
    public static final String GROUP = "api";
    public static final String JOBSTORE_STREAM = String.format("%s:%s", CHANNEL, STREAM);

    @Inject
    protected ServiceConfig service;

    @Inject
    protected TransferConfig transfer;

    @Inject
    OidcClient client;
    TokensHelper tokenHelper;

    private static AccountingService accounting;

    private final String instance;
    private final ReactiveStreamCommands<String, String, String> stream;
    private Cancellable consumer;


    /***
     * Construct with a data source
     * @param ds is the injected Redis data source
     */
    public AccountingCollector(ReactiveRedisDataSource ds) {
        this.tokenHelper = new TokensHelper();
        this.stream = null != ds ? ds.stream(String.class) : null;

        // Get a unique consumer name
        this.instance = DynamicConfiguration.getInstanceName();
    }

    /***
     * Starting listening to stream events after instance creation
     */
    @PostConstruct
    void onStart() {

        // Create REST client for the accounting service
        if(service.accounting().url().isEmpty() ||
           service.accounting().installation().isEmpty() ||
           service.accounting().metric().isEmpty())
            // No accounting server, nothing to do
            return;

        MDC.put("consumerId", this.instance);
        log.info("Accounting collector is starting...");

        MDC.put("accountingUrl", service.accounting().url().get());
        log.debug("Obtaining REST client for accounting server");

        // Check if accounting server base URL is valid
        URL accountingUrl = null;
        try {
            accountingUrl = new URL(service.accounting().url().get());
        } catch (MalformedURLException e) {
            log.error(e.getMessage());
            return;
        }

        try {
            // Create the REST client for the accounting server
            var tsFile = service.accounting().trustStoreFile().isPresent() ?
                         service.accounting().trustStoreFile().get() : "";
            var tsPass = service.accounting().trustStorePassword().isPresent() ?
                         service.accounting().trustStorePassword().get() : "";
            var ots = loadKeyStore(tsFile, tsPass, log);
            var rcb = RestClientBuilder.newBuilder().baseUrl(accountingUrl);

            if(ots.isPresent())
                rcb.trustStore(ots.get());

            accounting = rcb.build(AccountingService.class);
        }
        catch(RestClientDefinitionException | IllegalStateException e) {
            log.error(e.getMessage());
            return;
        }

        // Subscribe to job stream in Redis
        this.consumer = this.stream.xgroupCreate(JOBSTORE_STREAM, GROUP, "0-0")

            .onFailure().recoverWithNull()
            .map(unused -> this.stream.xgroupCreateConsumer(JOBSTORE_STREAM, GROUP, instance))
            .map(unused -> createStreamListener())
            .subscribe().with(cancellable -> this.consumer = cancellable);

        log.infof("Subscribed to channel %s", JOBSTORE_STREAM);
    }

    /***
     * Stop listening to stream events before instance destruction
     */
    @PreDestroy
    void onStop() {
        MDC.put("consumerId", this.instance);
        log.info("Accounting collector is stopping...");
        this.consumer.cancel();
        log.info("Canceled stream listener");
        this.stream.xgroupDelConsumer(JOBSTORE_STREAM, GROUP, this.instance)

            .subscribe()
            .with(unack -> {
                MDC.put("consumerId", this.instance);
                if(unack > 0)
                    log.infof("Accounting collector stopped with %d unacknowledged messages", unack);
                else
                    log.infof("Accounting collector stopped");
            });
    }

    /***
     * Subscribe to the jobs stream
     * @return object to be used to cancel this subscription
     */
    private Cancellable createStreamListener() {
        MDC.put("consumerId", this.instance);
        log.info("Creating stream listener");

        XReadGroupArgs args = new XReadGroupArgs()
                                    .block(Duration.ofSeconds(60))
                                    //.claim(Duration.ofSeconds(srvConfig.accounting().pollInterval()))
                                    .count(1);

        return Multi.createBy()

            .repeating()
            .uni(() ->
                // Create Multi stream by reading messages from the Redis stream
                this.stream
                    .xreadgroup(GROUP, this.instance, JOBSTORE_STREAM, ">", args)
                    .onFailure().recoverWithItem(emptyList())
            )
            .indefinitely()
            .flatMap(messages -> Multi.createFrom().iterable(messages))
            .onItem().transformToUniAndMerge(this::processMessage)
            .onFailure().recoverWithItem(e -> {
                // On error respond with empty message array, essentially a NOP
                MDC.put("consumerId", this.instance);
                log.errorf("Cannot process message (%s)", e.getMessage());
                return false;
            })
            .collect()
            .in(BooleanCounter::new, (acc, success) -> {
                acc.accumulateSuccess(success);
            })
            .onItem().transform(BooleanCounter::get)
            .subscribe()
            .with(count -> {
                if(count > 0) {
                    // Log how many jobs were handled
                    MDC.put("consumerId", this.instance);
                    log.debugf("Accounted for %d transfer job(s)", count);
                }
            });
    }

    /***
     * Check if a transfer job recorded in the stream has finished
     * @param message is a messages from the jobs stream
     * @return True if job has finished and was successfully accounted for (accounting record sent)
     */
    Uni<Boolean> processMessage(StreamMessage<String, String, String> message) {

        var badMessage = new AtomicReference<String>(null);
        var destination = new AtomicReference<String>(null);
        var jobId = new AtomicReference<String>(null);
        var userId = new AtomicReference<String>(null);

        try {
            var dest = message.payload().get("dest");
            if(null != dest)
                destination.set(dest);
            else
                badMessage.set("Empty destination");
        }
        catch(Exception e) {
            badMessage.set("No destination");
        }

        try {
            if(null == badMessage.get()) {
                var jid = message.payload().get("jobId");
                if (null != jid)
                    jobId.set(jid);
                else
                    badMessage.set("Empty jobId");
            }
        }
        catch(Exception e) {
            badMessage.set("No jobId");
        }

        try {
            if(null == badMessage.get()) {
                var uid = message.payload().get("userId");
                if(null != uid)
                    userId.set(uid);
                else
                    badMessage.set("Empty userId");
            }
        }
        catch(Exception e) {
            badMessage.set("No userId");
        }

        if(null != badMessage.get()) {
            // Remove malformed message from stream
            return this.stream.xdel(JOBSTORE_STREAM, message.id())
                              .chain(delCount -> {
                                  MDC.put("payload", message.payload().toString());
                                  log.errorf("Removed malformed stream message %s (%s)",
                                             message.id(), badMessage.get());
                                  return Uni.createFrom().item(false);
                              });
        }

        MDC.put("consumerId", this.instance);
        MDC.put("messageId", message.id());
        MDC.put("jobId", jobId.get());
        MDC.put("dest", destination.get());
        log.infof("Checking status of transfer %s", jobId.get());

        // Pick transfer service and create REST client for it
        final var ts = DataTransferBase.getTransferService(destination.get(), this.transfer, this.log, false);
        if(null == ts)
            // Could not the transfer engine used for this destination
            return Uni.createFrom().failure(new TransferServiceException("invalidServiceConfig"));

        var done = new AtomicReference<Boolean>(false);
        var token = new AtomicReference<String>(null);

        var props = new HashMap<String, String>();
        props.put(OidcConstants.TOKEN_SCOPE, "openid entitlements");
        props.put(OidcConstants.TOKEN_AUDIENCE_GRANT_PROPERTY, ts.getServiceUrl());

        return tokenHelper.getTokens(client, props, true)

            .chain(tokens -> {
                // Get transfer details
                var at = tokens.getAccessToken();
                token.set("Bearer " + at);
                return ts.getTransferInfo(token.get(), jobId.get(), FileDetails.all);
            })
            .onFailure().recoverWithItem(e -> {
                log.errorf("Failed to get status of transfer %s (%s)", jobId.get(), e.getMessage());
                return null;
            })
            .chain(transferInfo -> {
                if(null != transferInfo) {
                    // Got transfer details
                    MDC.put("jobState", transferInfo.jobState);
                    log.infof("Transfer %s is %s", jobId.get(), transferInfo.jobState.toString());

                    done.set(transferInfo.jobState == TransferState.failed ||
                             transferInfo.jobState == TransferState.partial ||
                             transferInfo.jobState == TransferState.canceled ||
                             transferInfo.jobState == TransferState.succeeded);

                    if(done.get() && transferInfo.payload.isPresent()) {
                        // Compute amount of data that was transferred
                        int filesTransferred = 0;
                        long bytesTransferred = 0;
                        var pi = transferInfo.payload.get();
                        for(var fileInfo : pi) {
                            if(fileInfo.fileState == FileState.succeeded) {
                                filesTransferred++;
                                if(fileInfo.size.isPresent())
                                    bytesTransferred += (fileInfo.size.get() * fileInfo.destinations);
                            }
                        }

                        if(null != accounting &&
                           service.accounting().installation().isPresent() &&
                           service.accounting().metric().isPresent()) {
                            // Send accounting record for this transfer
                            var installation = service.accounting().installation().get();
                            var usageRecord = new DataTransferUsageRecord();
                            usageRecord.metricId = service.accounting().metric().get();
                            usageRecord.bytesTransferred = bytesTransferred;
                            usageRecord.periodStart = transferInfo.submittedAt;
                            usageRecord.periodEnd = transferInfo.finishedAt;

                            if(null == userId.get())
                                userId.set(transferInfo.userId);

                            usageRecord.userId = Optional.of(userId.get());

                            return accounting.sendUsageRecord(token.get(), installation, usageRecord);
                        }
                    }
                }

                return Uni.createFrom().nullItem();
            })
            .onFailure().recoverWithItem(e -> {
                log.errorf("Failed to send accounting record for transfer %s (%s)", jobId.get(), e.getMessage());
                return null;
            })
            .chain(usageRecord -> {
                if(null != usageRecord)
                    log.infof("Sent accounting record for transfer %s", jobId.get());

                if(done.get()) {
                    // Transfer has finished, acknowledge message
                    final String[] messageIds = { message.id() };
                    return this.stream.xack(JOBSTORE_STREAM, GROUP, messageIds);
                }

                return Uni.createFrom().item(0);
            })
            .chain(ackCount -> {
                if(ackCount > 0)
                    // Remove acknowledged message from stream
                    return this.stream.xdel(JOBSTORE_STREAM, message.id());

                return Uni.createFrom().item(0);
            })
            .chain(delCount -> {
                return Uni.createFrom().item(done.get() && delCount > 0);
            });
    }
}
