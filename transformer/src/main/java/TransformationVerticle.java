import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
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

        vertx.eventBus().consumer("transform", message -> {

            JsonObject request = (JsonObject) message.body();
            String pipeId = request.getString("pipeId");
            JsonObject payload = request.getJsonObject("payload");

            LOG.debug("Transforming payload {}", payload.encode());

            StringBuilder sb = new StringBuilder();
//            sb.append("City,Time,Latitude,Longitude,Temperature\n");

            String city = payload.getString("name");

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

            sb.append(city).append(",");
            sb.append(date).append(",");
            sb.append(latitude).append(",");
            sb.append(longitude).append(",");
            sb.append(temp).append("\n");

            sendLine(pipeId, sb.toString());
        });

        future.complete();
    }

    private void sendLine(String pipeId, String payload) {
        LOG.debug("Sending line [{}]", payload);

        JsonObject message = new JsonObject();
        message.put("pipeId", pipeId);
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
