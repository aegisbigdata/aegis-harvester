import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import model.Constants;
import model.ImportRequest;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static model.Constants.TYPE_BBOX;

public class ImporterVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(ImporterVerticle.class);

    private HttpClient httpClient;

    @Override
    public void start(Future<Void> future) {
        vertx.eventBus().consumer(Constants.MSG_IMPORT, this::getWeatherData);

        httpClient = HttpClients.createDefault();
        future.complete();
    }

    private void getWeatherData(Message<String> message) {
        ImportRequest request = Json.decodeValue(message.body(), ImportRequest.class);

        String apiKey = config().getString("owmApiKey");
        String url = "http://api.openweathermap.org";

        StringBuilder params = new StringBuilder("/data/2.5/");     // StringBuilder so result is permitted in lambda expression

        if (TYPE_BBOX.equals(request.getType())) {
            params.append("box/city?bbox=").append(request.getValue());
        } else {
            params.append("weather?id=").append(request.getValue());
        }

        params.append("&APPID=").append(apiKey).append("&units=metric");

        long totalTicks = computeNumberOfTicks(request);
        AtomicLong triggerCounter = new AtomicLong(0);  // atomic so variable is permitted in lambda expression
        LOG.debug("Importing [{}] times for pipe with ID [{}]", totalTicks, request.getPipeId());

        getOwmApiData(url + params.toString(), request);   // periodic timer waits before first request

        // duration of 0 hours is defined to trigger exactly once
        if (request.getDurationInHours() > 0) {
            vertx.setPeriodic(request.getFrequencyInMinutes() * 60000, id -> {
                if (triggerCounter.get() == totalTicks) {
                    vertx.cancelTimer(id);
                    removeJobFromFile(config().getString("tmpDir") + "/" + Constants.JOB_FILE_NAME, request.getPipeId());
                    LOG.debug("Pipe with ID [{}] done", request.getPipeId());
                } else {
                    triggerCounter.addAndGet(1);
                    getOwmApiData(url + params.toString(), request);
                }
            });
        }
    }

    private void getOwmApiData(String url, ImportRequest request) {
        LOG.debug("Request URL: {}", url);

        vertx.executeBlocking(future -> {
            HttpGet httpGet = new HttpGet(url);

            try {
                HttpResponse response = httpClient.execute(httpGet);
                int status = response.getStatusLine().getStatusCode();
                JsonObject body = new JsonObject(EntityUtils.toString(response.getEntity()));

                LOG.debug("OWM API Response ({}): {}", response.getStatusLine().getStatusCode(), body);

                if (status < 200 || status > 400) {
                    LOG.warn("OWM API request returned status [{}]", status);
                    future.fail("Bad status code: " + status);
                } else {
                    List<Future> sendFutures = new ArrayList<>();

                    if (TYPE_BBOX.equals(request.getType())) {
                        for (Object obj : body.getJsonArray("list")) {
                            sendFutures.add(sendWeatherData(request, (JsonObject) obj));
                        }
                    } else {
                        sendFutures.add(sendWeatherData(request, body));
                    }

                    CompositeFuture.all(sendFutures).setHandler(handler -> {
                        if (handler.succeeded()) {
                            future.complete();
                        } else {
                            future.fail("At least one piece of data could not be sent");
                        }
                    });
                }
            } catch (IOException e) {
                e.printStackTrace();
                LOG.warn("GET to OWM API failed: {}", e.getMessage());
                future.fail("GET to OWM API failed: " + e.getMessage());
            }
        }, result -> {
            if (result.failed())
                LOG.debug("Importing weather data from URL [{}] failed: {}", url, result.cause());
        });
    }

    private Future<Void> sendWeatherData(ImportRequest request, JsonObject payload) {
        Future<Void> future = Future.future();

        String url = "http://"
                + config().getString("target.host") + ":"
                + config().getInteger("target.port")
                + config().getString("target.endpoint");

        JsonObject message = new JsonObject();
        message.put("pipeId", request.getPipeId());
        message.put("hopsFolder", request.getHopsFolder());
        message.put("payload", payload.toString());

        vertx.executeBlocking(handler -> {
            try {
                HttpPost postRequest = new HttpPost(url);
                postRequest.setHeader("Content-Type", "application/json");

                HttpEntity entity = new ByteArrayEntity(message.encode().getBytes("UTF-8"));
                postRequest.setEntity(entity);

                HttpResponse response = httpClient.execute(postRequest);
                int status = response.getStatusLine().getStatusCode();

                if (status < 200 || status > 400) {
                    LOG.warn("POST request returned status [{}]", status);
                    handler.fail("Bad status code: " + status);
                } else {
                    handler.complete();
                }
            } catch (IOException e) {
                handler.fail("POST request to [{}] failed: " + e.getMessage());
            }
        }, res -> {
            if (res.succeeded()) {
                future.complete();
            } else {
                LOG.debug("POST request to [{}] returned [{}]", url, res.result());
                future.fail(res.cause());
            }
        });

        return future;
    }

    // calculates the total number of times data should be read from an endpoint
    private long computeNumberOfTicks(ImportRequest request) {

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
