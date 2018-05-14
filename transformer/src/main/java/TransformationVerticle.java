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
import java.util.Date;

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

        StringBuilder sb = new StringBuilder();

        String location = payload.getString("name");

        // keys have different case depending on request type (bbox vs locationId)
        Double latitude = payload.getJsonObject("coord").getDouble("Lat") != null
                ? payload.getJsonObject("coord").getDouble("Lat")
                : payload.getJsonObject("coord").getDouble("lat");

        Double longitude = payload.getJsonObject("coord").getDouble("Lon") != null
                ? payload.getJsonObject("coord").getDouble("Lon")
                : payload.getJsonObject("coord").getDouble("lon");

        String temp = payload.getJsonObject("main").getDouble("temp").toString();
        Long timeStamp = payload.getLong("dt");
        String date = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new Date(timeStamp * 1000L));

        sb.append(location).append(",");
        sb.append(date).append(",");
        sb.append(latitude).append(",");
        sb.append(longitude).append(",");
        sb.append(temp).append("\n");

        sendLine(request, location, sb.toString());
    }

    private void sendLine(TransformationRequest request, String location, String payload) {
        LOG.debug("Sending line [{}]", payload);

        JsonObject message = new JsonObject();
        message.put("pipeId", request.getPipeId());
        message.put("hopsFolder", request.getHopsFolder());
        message.put("location", location.replaceAll("[^a-zA-Z]+","")); // remove special chars for use as file name
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
}
