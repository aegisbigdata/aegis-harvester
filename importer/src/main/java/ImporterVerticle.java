import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
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

    private void getWeatherData(Message<Object> message) {

        String bbox = message.body().toString();
        String apiKey = config().getString("owmApiKey");
        String url = "api.openweathermap.org";
        String params = "/data/2.5/box/city?bbox="+ bbox + "&appid=" + apiKey + "&units=metric";

        webClient
                .get(url, params)
                .as(BodyCodec.jsonObject())
                .send(ar -> {
                    if (ar.succeeded()) {
                        HttpResponse<JsonObject> response = ar.result();
                        JsonObject body = response.body();
                        sendToTransformer(body.getJsonArray("list"));
                    } else {
                        LOG.error("Something went wrong " + ar.cause().getMessage());
                    }
                });
    }

    private void sendToTransformer(JsonArray values) {

        JsonObject message = new JsonObject();
        message.put("pipeId", config().getString("pipeId"));
        message.put("values", values);

        String transformerUrl = config().getString("transformer.ip")
                + ":" + config().getInteger("transformer.port")
                + "/transform";

        webClient.postAbs(transformerUrl)
                .sendJson(message, postResult -> {
                    if (postResult.succeeded()) {
                        HttpResponse<Buffer> postResponse = postResult.result();

                        if (!(200 <= postResponse.statusCode() && postResponse.statusCode() < 400))
                            LOG.warn("Callback URL returned status [{}]", postResponse.statusCode());

                    } else {
                        LOG.warn("POST to [{}] failed: {}", transformerUrl, postResult.cause());
                    }
                });
    }
}
