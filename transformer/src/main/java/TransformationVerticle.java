import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;

public class TransformationVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

    private WebClient webClient;

    @Override
    public void start(Future<Void> future) {
        webClient = WebClient.create(vertx);

        vertx.eventBus().consumer("transform", message -> {

            JsonObject request = (JsonObject) message.body();
            JsonArray list = request.getJsonArray("payload");
            Iterator<Object> listItr = list.iterator();

            StringBuilder sb = new StringBuilder();
            sb.append("City,Time,Latitude,Longitude,Temperature\n");

            while (listItr.hasNext()) {
                JsonObject obj = (JsonObject) listItr.next();

                String city = obj.getString("name");
                String latitude = obj.getJsonObject("coord").getDouble("Lat").toString();
                String longitude = obj.getJsonObject("coord").getDouble("Lon").toString();
                String temp = obj.getJsonObject("main").getDouble("temp").toString();
                Long timeStamp = obj.getLong("dt");
                String date = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new Date(timeStamp * 1000L));

                sb.append(city).append(",");
                sb.append(date).append(",");
                sb.append(latitude).append(",");
                sb.append(longitude).append(",");
                sb.append(temp).append("\n");
            }

            sendLine(sb.toString());
        });
    }

    private void sendLine(String payload) {
        JsonObject message = new JsonObject();
        message.put("pipeId", config().getString("pipeId"));
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
