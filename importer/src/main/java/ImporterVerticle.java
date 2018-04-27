import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import model.Constants;
import model.ImportRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

import static model.Constants.TYPE_BBOX;

public class ImporterVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(ImporterVerticle.class);

    private WebClient webClient;

    @Override
    public void start(Future<Void> future) {
        vertx.eventBus().consumer(Constants.MSG_IMPORT, this::getWeatherData);

        webClient = WebClient.create(vertx);
        future.complete();
    }

    private void getWeatherData(Message<String> message) {
        ImportRequest request = Json.decodeValue(message.body(), ImportRequest.class);

        String apiKey = config().getString("owmApiKey");
        String url = "api.openweathermap.org/data/2.5/box/city";

        StringBuilder params = new StringBuilder("?appid=")     // StringBuilder so result is permitted in lambda expression
                .append(apiKey)
                .append("&units=metric");

        if (TYPE_BBOX.equals(request.getType())) {
            params.append("&bbox=").append(request.getValue());
        } else {
            params.append("&id=").append(request.getValue());
        }

        long totalTicks = computeNumberOfTicks(request);
        AtomicLong triggerCounter = new AtomicLong(0);  // atomic so variable is permitted in lambda expression

        vertx.setPeriodic(computeNumberOfTicks(request), id -> {
            if (triggerCounter.get() == totalTicks) {
                vertx.cancelTimer(id);
                message.reply(request.getPipeId());     // reply so ID is removed from running job list
            } else {
                triggerCounter.addAndGet(1);
                webClient
                        .get(url, params.toString())
                        .as(BodyCodec.jsonObject())
                        .send(ar -> {
                            if (ar.succeeded()) {
                                HttpResponse<JsonObject> response = ar.result();
                                JsonObject body = response.body();
                                sendWeatherData(body.getJsonArray("list"));
                            } else {
                                LOG.error("Something went wrong " + ar.cause().getMessage());
                            }
                        });
            }
        });
    }

    private void sendWeatherData(JsonArray payload) {

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

    private long computeNumberOfTicks(ImportRequest request) {

        // sanitize inputs
        int durationInHours = request.getDurationInHours() >= 0
                ? request.getDurationInHours()
                : 0;
        int frequencyInMinutes = request.getFrequencyInMinutes() >= 0
                ? request.getFrequencyInMinutes()
                : 0;

        return durationInHours > 0
                ? frequencyInMinutes / durationInHours * 60
                : 1;
    }
}