import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import model.*;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;
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

        // duration of 0 hours is defined to trigger exactly once
        if (request.getDurationInHours() > 0) {

            getCkanApiData(request, false); // periodic timer waits before first request

            vertx.setPeriodic(request.getFrequencyInMinutes() * 60000, id -> {
                if (triggerCounter.get() == totalTicks) {
                    vertx.cancelTimer(id);
                    removeJobFromFile(config().getString("tmpDir") + "/" + Constants.JOB_FILE_NAME, request.getPipeId());
                    LOG.debug("Pipe with ID [{}] done", request.getPipeId());
                } else {
                    triggerCounter.addAndGet(1);
                    getCkanApiData(request, false);
                }
            });
        } else {
            getCkanApiData(request, true);
        }
    }

    private void getCkanApiData(CkanFetchRequest request, boolean removeJobFromFile) {
        if (request.getFetchType().equals(CkanFetchType.URL)) {
            handleCkanUrlRequest(request, removeJobFromFile);
        } else {
            handleCkanResourceRequest(request, removeJobFromFile);
        }
    }

    private void handleCkanUrlRequest(CkanFetchRequest request, boolean removeJobFromFile) {
        issueHttpRequest(request.getUrl()).setHandler(ckanHandler -> {
            if (ckanHandler.succeeded()) {
                try {
                    AtomicInteger fileCount = new AtomicInteger(0);
                    for (Object resultObj : new JsonObject(ckanHandler.result()).getJsonObject("result").getJsonArray("results")) {
                        for (Object resourceObj : (((JsonObject) resultObj).getJsonArray("resources"))) {
                            JsonObject resource = (JsonObject) resourceObj;
                            String url = resource.getString("url");

                            if (url != null && url.endsWith(".csv")) {
                                String baseFileName = url.substring(url.lastIndexOf("/"), url.lastIndexOf("."));

                                getCsvFileFromUrl(url.replaceAll(" ", "%20"), baseFileName).setHandler(csvHandler -> {
                                    if (csvHandler.succeeded()) {
                                        JsonObject payload = new JsonObject().put("csv", csvHandler.result());

                                        // when uploading multiple files with the same pipeId, their file names will be overwritten in the aggregator
                                        DataSendRequest dataSendRequest =
                                                new DataSendRequest(request.getPipeId() + fileCount.incrementAndGet(), request.getHopsProjectId(), request.getHopsDataset(), DataType.CSV, baseFileName, payload.toString(), request.getUser(), request.getPassword());

                                        vertx.eventBus().send(Constants.MSG_SEND_DATA, Json.encode(dataSendRequest));
                                    } else {
                                        LOG.error("CSV handling failed: {}", csvHandler.cause());
                                    }
                                });
                            }
                        }
                    }
                } catch (Exception e) {
                    LOG.error("Exception thrown: {}", e.getMessage());
                }
            } else {
                LOG.error("CKAN import failed: {}", ckanHandler.cause());
            }

            if (removeJobFromFile) {
                removeJobFromFile(config().getString("tmpDir") + "/" + Constants.JOB_FILE_NAME, request.getPipeId());
                LOG.debug("Pipe with ID [{}] done", request.getPipeId());
            }
        });
    }

    private void handleCkanResourceRequest(CkanFetchRequest request, boolean removeJobFromFile) {
        String ckanUrl = request.getUrl() + "/api/3/action/resource_show?id=" + request.getResourceId();
        issueHttpRequest(ckanUrl).setHandler(ckanHandler -> {
            if (ckanHandler.succeeded()) {
                try {
                    JsonObject resource = new JsonObject(ckanHandler.result());

                    if (resource.getBoolean("success")) {
                        String resourceUrl = resource.getJsonObject("result").getString("url");

                        if (resourceUrl != null && resourceUrl.endsWith(".csv")) {
                            String baseFileName = resourceUrl.substring(resourceUrl.lastIndexOf("/"), resourceUrl.lastIndexOf("."));

                            getCsvFileFromUrl(resourceUrl.replaceAll(" ", "%20"), baseFileName).setHandler(csvHandler -> {
                                if (csvHandler.succeeded()) {
                                    JsonObject payload = new JsonObject().put("csv", csvHandler.result());

                                    // when uploading multiple files with the same pipeId, their file names will be overwritten in the aggregator
                                    DataSendRequest dataSendRequest =
                                            new DataSendRequest(request.getPipeId(), request.getHopsProjectId(), request.getHopsDataset(), DataType.CSV, baseFileName, payload.toString(), request.getUser(), request.getPassword());

                                    vertx.eventBus().send(Constants.MSG_SEND_DATA, Json.encode(dataSendRequest));
                                } else {
                                    LOG.error("CSV handling failed: {}", csvHandler.cause());
                                }
                            });
                        }
                    } else {
                        LOG.error("Resource with ID [{}] not found at [{}]", request.getResourceId(), ckanUrl);
                    }
                } catch (Exception e) {
                    LOG.error("Exception thrown: {}", e.getMessage());
                }
            } else {
                LOG.error("CKAN import failed: {}", ckanHandler.cause());
            }

            if (removeJobFromFile) {
                removeJobFromFile(config().getString("tmpDir") + "/" + Constants.JOB_FILE_NAME, request.getPipeId());
                LOG.debug("Pipe with ID [{}] done", request.getPipeId());
            }
        });
    }

    private Future<String> issueHttpRequest(String url) {
        Future<String> resultFuture = Future.future();
        LOG.debug("Issuing GET request to [{}]", url);

        vertx.executeBlocking(httpFuture -> {
            HttpGet httpGet = new HttpGet(url);

            try {
                HttpResponse response = httpClient.execute(httpGet);
                int status = response.getStatusLine().getStatusCode();
                String body = EntityUtils.toString(response.getEntity());

                // LOG.debug("BODY {}", body);

                if (status < 200 || status > 400) {
                    resultFuture.fail("Ckan API returned status code: " + status);
                } else {
                    resultFuture.complete(body);
                }
            } catch (IOException e) {
                resultFuture.fail("GET to Ckan API failed: " + e.getMessage());
            }
        }, result -> {
        });

        return resultFuture;
    }

    private Future<String> getCsvFileFromUrl(String url, String fileName) {
        Future<String> resultFuture = Future.future();
        LOG.debug("Downloading file from [{}]", url);

        vertx.executeBlocking(httpFuture -> {
            try {
                File csvFile = new File(config().getString("tmpDir") + fileName);
                FileUtils.copyURLToFile(new URL(url), csvFile, 10000, 10000);
                String csvContent = FileUtils.readFileToString(csvFile, "UTF-8");
                resultFuture.complete(csvContent);
            } catch (IOException e) {
                resultFuture.fail("Failed to download file: " + e.getMessage());
            }
        }, result -> {});

        return resultFuture;
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
