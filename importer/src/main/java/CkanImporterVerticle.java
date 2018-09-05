import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.core.json.JsonArray;
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

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;

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
        } else if(request.getResourceId() != null) {
            handleCkanResourceRequest(request, removeJobFromFile);
        } else if(request.getPackageId() != null) {
            handleCkanPackageRequest(request, removeJobFromFile);
        } else {
            LOG.warn("No resourceId or packageId provided for FetchType ID");
        }
    }

    private void handleCkanUrlRequest(CkanFetchRequest request, boolean removeJobFromFile) {
        issueHttpRequest(request.getUrl()).setHandler(ckanHandler -> {
            if (ckanHandler.succeeded()) {
                try {
                    AtomicInteger fileCount = new AtomicInteger(0);
                    Integer countCSV = 0;
                    for (Object resultObj : new JsonObject(ckanHandler.result()).getJsonObject("result").getJsonArray("results")) {

                        for (Object resourceObj : (((JsonObject) resultObj).getJsonArray("resources"))) {
                            JsonObject resource = (JsonObject) resourceObj;

                            String url = resource.getString("url");
                            String format = resource.getString("format");

                            //if (url != null && (format != null && format.equals("CSV") || url.endsWith(".csv"))) {
                            if (url != null && url.endsWith(".csv")) {

                                String pId = request.getPipeId() + fileCount.incrementAndGet();
                                countCSV++;

                                String baseFileName;

                                if(url.endsWith(".csv")) {
                                    baseFileName = url.substring(url.lastIndexOf("/"), url.lastIndexOf("."));
                                } else {
                                    baseFileName = url.substring(url.lastIndexOf("/"), url.length()-1);
                                }

                                LOG.debug("url : [{}]", url);
                                LOG.debug("baseFileName : [{}]", baseFileName);
                                LOG.debug("pipeId : [{}]", pId);

                                // when uploading multiple files with the same pipeId, their file names will be overwritten in the aggregator
                                CSVDownloadRequest csvDownloadRequest =
                                        new CSVDownloadRequest(
                                                pId,
                                                request.getHopsProjectId(),
                                                request.getHopsDataset(),
                                                DataType.CSV,
                                                baseFileName,
                                                url,
                                                request.getUser(),
                                                request.getPassword(),
                                                createMetadata((JsonObject) resultObj, resource)
                                        );

                                vertx.eventBus().send(Constants.MSG_DOWNLOAD_CSV, Json.encode(csvDownloadRequest));

                            }
                        }
                    }
                    LOG.debug("Found [{}] CSV files", countCSV);
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

    private void handleCkanPackageRequest(CkanFetchRequest request, boolean removeJobFromFile) {
        String ckanUrl = request.getUrl() + "/api/3/action/package_show?id=" + request.getPackageId();
        issueHttpRequest(ckanUrl).setHandler(ckanHandler -> {
            if (ckanHandler.succeeded()) {
                try {
                    AtomicInteger fileCount = new AtomicInteger(0);
                    Integer countCSV = 0;

                    JsonObject ckanPackage = new JsonObject(ckanHandler.result()).getJsonObject("result");

                    boolean containsCSV = false;

                    for (Object resourceObj : ckanPackage.getJsonArray("resources")) {
                        JsonObject resource = (JsonObject) resourceObj;

                        String url = resource.getString("url");

                        if (url != null && url.endsWith(".csv")) {

                            containsCSV = true;

                            String baseFileName = url.substring(url.lastIndexOf("/"), url.lastIndexOf("."));

                            LOG.debug("baseFileName : [{}]", baseFileName);

                            String pId = request.getPipeId() + fileCount.incrementAndGet();

                            LOG.debug("url : [{}]", url);
                            LOG.debug("pipeId : [{}]", pId);

                            // when uploading multiple files with the same pipeId, their file names will be overwritten in the aggregator
                            CSVDownloadRequest csvDownloadRequest =
                                    new CSVDownloadRequest(
                                            pId,
                                            request.getHopsProjectId(),
                                            request.getHopsDataset(),
                                            DataType.CSV,
                                            baseFileName,
                                            url,
                                            request.getUser(),
                                            request.getPassword(),
                                            createMetadata(ckanPackage, resource)
                                    );

                            vertx.eventBus().send(Constants.MSG_DOWNLOAD_CSV, Json.encode(csvDownloadRequest));
                        }
                    }

                    if(containsCSV) {
                        countCSV++;
                    }

                    LOG.debug("Found [{}] datasets with CSV", countCSV);
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

                            // when uploading multiple files with the same pipeId, their file names will be overwritten in the aggregator
                            CSVDownloadRequest csvDownloadRequest =
                                    new CSVDownloadRequest(
                                            request.getPipeId(),
                                            request.getHopsProjectId(),
                                            request.getHopsDataset(),
                                            DataType.CSV,
                                            baseFileName,
                                            resourceUrl,
                                            request.getUser(),
                                            request.getPassword(),
                                            createMetadata(null, resource.getJsonObject("result"))
                                    );

                            vertx.eventBus().send(Constants.MSG_DOWNLOAD_CSV, Json.encode(csvDownloadRequest));

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
            File csvFile = new File(config().getString("tmpDir") + fileName);

            try {
                FileUtils.copyURLToFile(new URL(url), csvFile, 10000, 10000);
                String csvContent = FileUtils.readFileToString(csvFile, "UTF-8");
                FileUtils.deleteQuietly(csvFile);
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

    private String createMetadata(JsonObject packageJson, JsonObject resourceJson) {
        JsonObject metadata = new JsonObject();

        if(packageJson != null) {

            JsonObject dataset = new JsonObject();

            if(packageJson.getString("title") == null) {
                dataset.put("title", "");
            } else {
                dataset.put("title", replaceUmlauts(packageJson.getString("title")));
            }

            if(packageJson.getString("notes") == null) {
                dataset.put("description", "");
            } else {
                dataset.put("description", replaceUmlauts(packageJson.getString("notes")));
            }

            dataset.put("publisher", new JsonObject().put("name", "").put("homepage", ""));
            dataset.put("contact_point", new JsonObject().put("name", "").put("email", ""));
            dataset.put("keywords", new JsonArray());
            dataset.put("themes", new JsonArray());
            dataset.putNull("catalog");

            metadata.put("dataset", dataset);
        } else {
            metadata.putNull("dataset");
        }

        JsonObject distribution = new JsonObject();

        if(resourceJson.getString("name") == null) {
            distribution.put("title", "");
        } else {
            distribution.put("title", replaceUmlauts(resourceJson.getString("name")));
        }

        if(resourceJson.getString("description") == null) {
            distribution.put("description", "");
        } else {
            distribution.put("description", replaceUmlauts(resourceJson.getString("description")));
        }

        if(resourceJson.getString("url") == null) {
            distribution.put("url", "");
        } else {
            distribution.put("access_url", resourceJson.getString("url").replaceAll(" ", ""));
        }

        if(resourceJson.getString("format") == null) {
            distribution.put("format", "");
        } else {
            distribution.put("format",  resourceJson.getString("format"));
        }

        if(resourceJson.getJsonObject("license") == null) {
            distribution.put("license", "");
        } else {
            distribution.put("license", resourceJson.getJsonObject("license").getString("resource"));

            if(distribution.getString("license") == null) {
                distribution.put("license", resourceJson.getJsonObject("license").getString("label"));
            }
        }

        metadata.put("distribution", distribution);

        return metadata.toString();
    }

    private static String replaceUmlauts(String input) {

        return input
                .replaceAll("ü", "ue")
                .replaceAll("ö", "oe")
                .replaceAll("ä", "ae")
                .replaceAll("ß", "ss")
                .replaceAll("Ü(?=[a-zäöüß ])", "Ue")
                .replaceAll("Ö(?=[a-zäöüß ])", "Oe")
                .replaceAll("Ä(?=[a-zäöüß ])", "Ae")
                .replaceAll("Ü", "UE")
                .replaceAll("Ö", "OE")
                .replaceAll("Ä", "AE");
    }
}
