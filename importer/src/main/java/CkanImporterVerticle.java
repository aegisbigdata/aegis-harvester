import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import model.*;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

public class CkanImporterVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(CkanImporterVerticle.class);

    private HttpClient httpClient;

    @Override
    public void start(Future<Void> future) {
        vertx.eventBus().consumer(DataType.CKAN.getEventBusAddress(), this::fetchCkanData);

        httpClient = HttpClients.createDefault();
        future.complete();
    }

    private void fetchCkanData(Message<String> message) {
        CkanFetchRequest request = Json.decodeValue(message.body(), CkanFetchRequest.class);

        long totalTicks = computeNumberOfTicks(request);
        AtomicLong triggerCounter = new AtomicLong(0);  // atomic so variable is permitted in lambda expression
        LOG.debug("Importing [{}] times for pipe with ID [{}]", totalTicks, request.getPipeId());

        getCkanApiData(request);   // periodic timer waits before first request

        // duration of 0 hours is defined to trigger exactly once
        if (request.getDurationInHours() > 0) {
            vertx.setPeriodic(request.getFrequencyInMinutes() * 60000, id -> {
                if (triggerCounter.get() == totalTicks) {
                    vertx.cancelTimer(id);
                    removeJobFromFile(config().getString("tmpDir") + "/" + Constants.JOB_FILE_NAME, request.getPipeId());
                    LOG.debug("Pipe with ID [{}] done", request.getPipeId());
                } else {
                    triggerCounter.addAndGet(1);
                    getCkanApiData(request);
                }
            });
        }
    }

    private void getCkanApiData(CkanFetchRequest request) {
        vertx.executeBlocking(future -> {
            HttpGet httpGet = new HttpGet(request.getUrl());

            try {
                HttpResponse response = httpClient.execute(httpGet);
                int status = response.getStatusLine().getStatusCode();
                JsonObject body = new JsonObject(EntityUtils.toString(response.getEntity()));

                LOG.debug("Ckan API Response ({}): {}", response.getStatusLine().getStatusCode(), body);

                if (status < 200 || status > 400) {
                    LOG.warn("OWM API request returned status [{}]", status);
                    future.fail("Bad status code: " + status);
                } else {
                    //TODO handle ckan api response

                    DataSendRequest dataSendRequest =
                            new DataSendRequest(request.getPipeId(), request.getHopsProjectId(), request.getHopsDataset(), DataType.OWM, body.toString());
                    vertx.eventBus().send(Constants.MSG_SEND_DATA, Json.encode(dataSendRequest));
                }
            } catch (IOException e) {
                e.printStackTrace();
                LOG.warn("GET to Ckan API failed: {}", e.getMessage());
                future.fail("GET to Ckan API failed: " + e.getMessage());
            }
        }, result -> {
            if (result.failed())
                LOG.debug("Importing data from URL [{}] failed: {}", request.getUrl(), result.cause());
        });
    }

    // calculates the total number of times data should be read from an endpoint
    private long computeNumberOfTicks(CkanFetchRequest request) {

        // sanitize inputs
        int durationInHours = request.getDurationInHours() >= 0
                ? request.getDurationInHours()
                : 0;
        int frequencyInMinutes = request.getFrequencyInMinutes() >= 0
                ? request.getFrequencyInMinutes()
                : 0;

        return durationInHours > 0
                ? (durationInHours * 60) / frequencyInMinutes
                : 1;
    }

    private void removeJobFromFile(String filePath, String jobId) {
        vertx.fileSystem().readFile(filePath, readHandler -> {
            if (readHandler.succeeded()) {
                String content = readHandler.result().toString().replace(jobId + "\n", "");
                vertx.fileSystem().writeFile(filePath, Buffer.buffer(content), writeHandler -> {
                    if (writeHandler.failed())
                        LOG.warn("Could not delete job [{}] from file [{}]: {}", jobId, filePath, writeHandler.cause());
                });
            } else {
                LOG.warn("File [{}] does not exist, skipping deletion", filePath);
            }
        });
    }
}
