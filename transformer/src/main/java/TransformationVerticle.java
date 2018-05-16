import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import model.Constants;
import model.TransformationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.*;

public class TransformationVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

    private WebClient webClient;

    @Override
    public void start(Future<Void> future) {
        webClient = WebClient.create(vertx);

        vertx.eventBus().consumer(Constants.MSG_TRANSFORM, this::handleTransformation);

        future.complete();
    }

    private void handleTransformation(Message<String> message) {
        TransformationRequest request = Json.decodeValue(message.body(), TransformationRequest.class);

        LOG.debug("Transforming {}", request);

        JsonObject payload = new JsonObject(request.getPayload());
        String location = payload.getString("name");

        List<String> csvValues = new ArrayList<>();
        csvValues.add(location);

        Long timeStamp = payload.getLong("dt");
        csvValues.add(timeStamp != null
                ? new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new Date(timeStamp * 1000L))
                : "");

        JsonObject coordinates = payload.getJsonObject("coord");
        if (coordinates != null) {
            // keys have different case depending on request type (bbox vs locationId)
            csvValues.add(coordinates.getDouble("Lat") != null
                    ? coordinates.getDouble("Lat") != null ? coordinates.getDouble("Lat").toString() : ""
                    : coordinates.getDouble("lat") != null ? coordinates.getDouble("lat").toString() : "");

            csvValues.add(coordinates.getDouble("Lon") != null
                    ? coordinates.getDouble("Lon") != null ? coordinates.getDouble("Lon").toString() : ""
                    : coordinates.getDouble("lon") != null ? coordinates.getDouble("lon").toString() : "");
        } else {
            // make sure not to break csv structure on missing values
            addEmptyValues(csvValues,2);
        }

        JsonObject main = payload.getJsonObject("main");
        if (main != null) {
            csvValues.add(main.getDouble("temp") != null ? main.getDouble("temp").toString() : "");
            csvValues.add(main.getDouble("pressure") != null ? main.getDouble("pressure").toString() : "");
            csvValues.add(main.getDouble("humidity") != null ? main.getDouble("humidity").toString() : "");
            csvValues.add(main.getDouble("temp_min") != null ? main.getDouble("temp_min").toString() : "");
            csvValues.add(main.getDouble("temp_max") != null ? main.getDouble("temp_max").toString() : "");
            csvValues.add(main.getDouble("visibility") != null ? main.getDouble("visibility").toString() : "");
        } else {
            // make sure not to break csv structure on missing values
            addEmptyValues(csvValues,6);
        }

        JsonObject wind = payload.getJsonObject("wind");
        if (wind != null) {
            csvValues.add(wind.getDouble("speed") != null ? wind.getDouble("speed").toString() : "");
            csvValues.add(wind.getDouble("deg") != null ? wind.getDouble("deg").toString() : "");
        } else {
            addEmptyValues(csvValues, 2);
        }

        JsonObject clouds = payload.getJsonObject("clouds");
        if (clouds != null) {
            csvValues.add(clouds.getDouble("all") != null ? clouds.getDouble("all").toString() : "");
        } else {
            addEmptyValues(csvValues, 1);
        }

        sendLine(request, location, String.join(",", csvValues) + "\n");
    }

    private void sendLine(TransformationRequest request, String location, String payload) {
        LOG.debug("Sending line [{}]", payload);

        JsonObject message = new JsonObject();
        message.put("pipeId", request.getPipeId());
        message.put("hopsFolder", request.getHopsFolder());
        message.put("location", location != null ? location.replaceAll("[^a-zA-Z]+","") : ""); // remove special chars for use as file name
        message.put("csvHeaders", "Location,Time,Latitude,Longitude,Avg. Temperature,Pressure,Humidity," +
                "Min. Temperature,Max. Temperature,Visibility,Wind Speed,Wind Direction,Cloudiness");
        message.put("payload", payload);

        Integer port = config().getInteger("target.port");
        String host = config().getString("target.host");
        String requestURI = config().getString("target.endpoint");

        webClient.post(port, host, requestURI)
                .sendJson(message, postResult -> {
                    if (postResult.succeeded()) {
                        HttpResponse<Buffer> postResponse = postResult.result();

                        if (!(200 <= postResponse.statusCode() && postResponse.statusCode() < 400))
                            LOG.warn("Callback URL returned status [{}]", postResponse.statusCode());

                    } else {
                        LOG.warn("POST to [{}] on port [{}] failed: {}", host + requestURI, port, postResult.cause());
                    }
                });
    }

    private void addEmptyValues(List<String> list, int count) {
        for (int i = 0; i < count; i++)
            list.add("");
    }
}
