import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.DecodeException;
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

public class ImporterVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(ImporterVerticle.class);

    private WebClient webClient;

    @Override
    public void start(Future<Void> future) {
        vertx.eventBus().consumer(Constants.MSG_IMPORT, this::getWeatherData);

        webClient = WebClient.create(vertx);
    }

    private void getWeatherData(Message<String> message) {

        try {
            ImportRequest request = Json.decodeValue(message.body(), ImportRequest.class);

            String apiKey = config().getString("owmApiKey");
            String url = "api.openweathermap.org/data/2.5/box/city";
            String params = "?appid=" + apiKey + "&units=metric";

            params += Constants.TYPE_BBOX.equals(request.getType())
                    ? "&bbox=" + request.getValue()
                    : "&id=" + request.getValue();

            webClient
                    .get(url, params)
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
        } catch (DecodeException e) {
            LOG.warn("Received invalid request: {}", message.body());
        }
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
}
