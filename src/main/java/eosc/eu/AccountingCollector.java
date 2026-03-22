package eosc.eu;

import eosc.eu.model.TransferInfo;
import grnet.model.DataTransferUsageRecord;
import org.jboss.logging.Logger;
import org.jboss.logging.MDC;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.subscription.Cancellable;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.eclipse.microprofile.rest.client.RestClientDefinitionException;
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

import grnet.AccountingService;

import eosc.eu.model.TransferPayloadInfo.FileDetails;
import eosc.eu.model.TransferInfoExtended.TransferState;
import eosc.eu.model.TransferPayloadInfo.FileState;


@Startup
@ApplicationScoped
public class AccountingCollector {

    private final Logger log = Logger.getLogger(AccountingCollector.class);

    public static final String CHANNEL = "transfer";
    public static final String STREAM = "jobs";
    public static final String GROUP = "api";
    public static final String JOBSTORE_STREAM = String.format("%s:%s", CHANNEL, STREAM);

    @Inject
    protected ServiceConfig srvConfig;

    @Inject
    protected PortConfig portConfig;

    private static DataTransferSelf self;
    private static AccountingService accounting;

    private final String instance;
    private final ReactiveStreamCommands<String, String, String> stream;
    private Cancellable consumer;


    /***
     * Construct with a data source
     * @param ds is the injected Redis data source
     */
    public AccountingCollector(ReactiveRedisDataSource ds) {
        this.stream = null != ds ? ds.stream(String.class) : null;

        // Get a unique consumer name
        this.instance = DynamicConfiguration.getInstanceName();
    }

    /***
     * Starting listening to stream events after instance creation
     */
    @PostConstruct
    void onStart() {
        // Prepare to call ourselves to retrieve transfer info
        MDC.put("consumerId", this.instance);
        log.debug("Obtaining REST client for ourselves");

        URL urlParserService;
        try {
            var urlSelf = String.format("http://localhost:%d", portConfig.port());
            urlParserService = new URL(urlSelf);
        } catch (MalformedURLException e) {
            log.error(e.getMessage());
            return;
        }

        try {
            // Create the REST client for ourselves
            self = RestClientBuilder.newBuilder()
                    .baseUrl(urlParserService)
                    .build(DataTransferSelf.class);
        }
        catch(RestClientDefinitionException e) {
            log.errorf("Failed to create REST client for ourselves (%s)", e.getMessage());
        }

        if(null == self) {
            log.error("No REST client to call ourselves");
            return;
        }

        // Prepare to call the accounting server


        // Subscribe to job stream in Redis
        log.info("Accounting collector is starting...");

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

        final String destination = message.payload().get("destination");
        final String jobId = message.payload().get("jobId");

        MDC.put("consumerId", this.instance);
        MDC.put("messageId", message.id());
        MDC.put("jobId", jobId);
        MDC.put("dest", destination);
        log.infof("Checking status of transfer %s", jobId);

        var done = new AtomicReference<Boolean>(false);

        return Uni.createFrom().nullItem()

            .chain(unused -> {
                // Get transfer details
                return self.getTransferInfo(jobId, destination, FileDetails.all);
            })
            .onFailure().recoverWithItem(e -> {
                log.errorf("Failed to get status of transfer %s (%s)", jobId, e.getMessage());
                return null;
            })
            .chain(transferInfo -> {
                if(null != transferInfo) {
                    // Got transfer details
                    done.set(transferInfo.jobState == TransferState.failed ||
                            transferInfo.jobState == TransferState.partial ||
                            transferInfo.jobState == TransferState.canceled ||
                            transferInfo.jobState == TransferState.succeeded);

                    if(done.get()) {
                        MDC.put("jobState", transferInfo.jobState);
                        log.infof("Transfer %s is %s", jobId, transferInfo.jobState);

                        if(transferInfo.payload_info.isPresent()) {
                            // Compute amount of data that was transferred
                            int filesTransferred = 0;
                            long bytesTransferred = 0;
                            var pi = transferInfo.payload_info.get();
                            for(var fileInfo : pi) {
                                if(fileInfo.fileState == FileState.succeeded) {
                                    filesTransferred++;
                                    if(fileInfo.size.isPresent())
                                        // TODO: Update after CERN returns total size for all destinations of a file
                                        bytesTransferred += fileInfo.size.get();
                                }
                            }

                            if(null != accounting &&
                               srvConfig.accounting().installation().isPresent() &&
                               srvConfig.accounting().metric().isPresent()) {
                                // Send accounting record for this transfer
                                var installation = srvConfig.accounting().installation().get();
                                var usageRecord = new DataTransferUsageRecord();
                                usageRecord.metricId = srvConfig.accounting().metric().get();
                                usageRecord.bytesTransferred = bytesTransferred;
                                usageRecord.userId = Optional.of(message.payload().get("userId"));
                                usageRecord.periodStart = transferInfo.submittedAt;
                                usageRecord.periodEnd = transferInfo.finishedAt;

                                return accounting.sendUsageRecord(installation, usageRecord);
                            }
                        }
                    }
                }

                return Uni.createFrom().nullItem();
            })
            .onFailure().recoverWithItem(e -> {
                log.errorf("Failed to send accounting record for transfer %s (%s)", jobId, e.getMessage());
                return null;
            })
            .chain(usageRecord -> {
                if(null != usageRecord)
                    log.infof("Sent accounting record for transfer %s", jobId);

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
                    return this.stream.xdel(JOBSTORE_STREAM, GROUP, message.id());

                return Uni.createFrom().item(0);
            })
            .chain(delCount -> {
                return Uni.createFrom().item(done.get() && delCount > 0);
            });
    }
}
