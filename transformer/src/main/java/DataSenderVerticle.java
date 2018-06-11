import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import model.Constants;
import model.DataSendRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class DataSenderVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

    private WebClient webClient;

    @Override
    public void start(Future<Void> future) {
        webClient = WebClient.create(vertx);

        vertx.eventBus().consumer(Constants.MSG_SEND, this::sendLine);

        future.complete();
    }

    private void sendLine(Message<String> message) {
        DataSendRequest request = Json.decodeValue(message.body(), DataSendRequest.class);

        LOG.debug("Sending line [{}]", request.getCsvPayload());

        JsonObject json = new JsonObject();
        json.put("pipeId", request.getPipeId());
        json.put("hopsFolder", request.getHopsFolder());
        json.put("location", request.getLocation() != null
                ? request.getLocation().replaceAll("[^a-zA-Z]+","") // remove special chars for use as file name
                : "");
        json.put("csvHeaders", request.getCsvHeaders());
        json.put("payload", request.getCsvPayload());

        Integer port = config().getInteger("target.port");
        String host = config().getString("target.host");
        String requestURI = config().getString("target.endpoint");

        webClient.post(port, host, requestURI)
                .sendJson(json, postResult -> {
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
