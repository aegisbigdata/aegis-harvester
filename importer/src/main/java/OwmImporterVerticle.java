import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import model.Constants;
import model.DataSendRequest;
import model.DataType;
import model.OwmFetchRequest;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

import static model.Constants.OWM_TYPE_BBOX;

public class OwmImporterVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(OwmImporterVerticle.class);

    private HttpClient httpClient;

    @Override
    public void start(Future<Void> future) {
        vertx.eventBus().consumer(DataType.OWM.getEventBusAddress(), this::getWeatherData);

        httpClient = HttpClients.createDefault();
        future.complete();
    }

    private void getWeatherData(Message<String> message) {
        OwmFetchRequest request = Json.decodeValue(message.body(), OwmFetchRequest.class);

        String apiKey = config().getString("owmApiKey");
        String owmUrl = "http://api.openweathermap.org";
        String destinationUrl = "http://"
                + config().getString("target.host") + ":"
                + config().getInteger("target.port")
                + config().getString("target.endpoint");

        StringBuilder params = new StringBuilder("/data/2.5/");     // StringBuilder so result is permitted in lambda expression

        if (OWM_TYPE_BBOX.equals(request.getType())) {
            params.append("box/city?bbox=").append(request.getValue());
        } else {
            params.append("weather?id=").append(request.getValue());
        }

        params.append("&APPID=").append(apiKey).append("&units=metric");

        long totalTicks = computeNumberOfTicks(request);
        AtomicLong triggerCounter = new AtomicLong(0);  // atomic so variable is permitted in lambda expression
        LOG.debug("Importing [{}] times for pipe with ID [{}]", totalTicks, request.getPipeId());

        getOwmApiData(owmUrl + params.toString(), destinationUrl, request);   // periodic timer waits before first request

        // duration of 0 hours is defined to trigger exactly once
        if (request.getDurationInHours() > 0) {
            vertx.setPeriodic(request.getFrequencyInMinutes() * 60000, id -> {
                if (triggerCounter.get() == totalTicks) {
                    vertx.cancelTimer(id);
                    removeJobFromFile(config().getString("tmpDir") + "/" + Constants.JOB_FILE_NAME, request.getPipeId());
                    LOG.debug("Pipe with ID [{}] done", request.getPipeId());
                } else {
                    triggerCounter.addAndGet(1);
                    getOwmApiData(owmUrl + params.toString(), destinationUrl, request);
                }
            });
        }
    }

    private void getOwmApiData(String owmUrl, String destinationUrl, OwmFetchRequest request) {
        LOG.debug("Request URL: {}", owmUrl);

        vertx.executeBlocking(future -> {
            HttpGet httpGet = new HttpGet(owmUrl);

            try {
                HttpResponse response = httpClient.execute(httpGet);
                int status = response.getStatusLine().getStatusCode();
                JsonObject body = new JsonObject(EntityUtils.toString(response.getEntity()));

                LOG.debug("OWM API Response ({}): {}", response.getStatusLine().getStatusCode(), body);

                if (status < 200 || status > 400) {
                    LOG.warn("OWM API request returned status [{}]", status);
                    future.fail("Bad status code: " + status);
                } else {
                    if (OWM_TYPE_BBOX.equals(request.getType())) {
                        for (Object obj : body.getJsonArray("list")) {
                            DataSendRequest dataSendRequest =
                                    new DataSendRequest(request.getPipeId(), request.getHopsFolder(), destinationUrl, DataType.OWM, (String) obj);
                            vertx.eventBus().send(Constants.MSG_SEND_DATA, Json.encode(dataSendRequest));
                        }
                    } else {
                        DataSendRequest dataSendRequest =
                                new DataSendRequest(request.getPipeId(), request.getHopsFolder(), destinationUrl, DataType.OWM, body.toString());
                        vertx.eventBus().send(Constants.MSG_SEND_DATA, Json.encode(dataSendRequest));
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
                LOG.warn("GET to OWM API failed: {}", e.getMessage());
                future.fail("GET to OWM API failed: " + e.getMessage());
            }
        }, result -> {
            if (result.failed())
                LOG.debug("Importing weather data from URL [{}] failed: {}", owmUrl, result.cause());
        });
    }

    // calculates the total number of times data should be read from an endpoint
    private long computeNumberOfTicks(OwmFetchRequest request) {

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
