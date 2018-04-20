package de.fhg.fokus.viaduct.consus.aegis.weatherdemo;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.codec.BodyCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ImportVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportVerticle.class);

    @Override
    public void start(Future<Void> future) {
        LOGGER.info("Started ImportVerticle");
        vertx.eventBus().consumer(Constants.MSG_IMPORT, this::getWeatherData);
    }

    private void getWeatherData(Message<Object> message) {
        String bbox = message.body().toString();
        WebClient webClient = WebClient.create(vertx);
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
                    JsonArray list = body.getJsonArray("list");
                    vertx.eventBus().send(Constants.MSG_TRANSFORM, list);
                } else {
                    LOGGER.error("Something went wrong " + ar.cause().getMessage());
                }
            });
    }
}
