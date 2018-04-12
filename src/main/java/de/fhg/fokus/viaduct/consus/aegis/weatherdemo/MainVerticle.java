package de.fhg.fokus.viaduct.consus.aegis.weatherdemo;

import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(MainVerticle.class);

    @Override
    public void start() {
        LOGGER.info(config().getString("Starting MainVerticle"));
        Future<Void> steps = loadConfig()
                .compose(this::bootstrapVerticles)
                .compose(this::startServer);
    }

    private Future<Void> startServer(JsonObject config) {
        Future<Void> future = Future.future();
        Integer port = config.getInteger("port");
        HttpServer server = vertx.createHttpServer();
        Router router = Router.router(vertx);
        router.get("/").handler(this::indexHandler);
        router.get("/weather").handler(this::weatherHandler);
        server
            .requestHandler(router::accept)
            .listen(port);
        LOGGER.info("Listening on port " + port.toString());
        return future;
    }

    private Future<JsonObject> loadConfig() {
        Future<JsonObject> future = Future.future();
        ConfigRetriever retriever = ConfigRetriever.create(vertx);
        retriever.getConfig(ar -> {
            if(ar.failed()) {
                future.failed();
            } else {
                future.complete(ar.result());
            }
        });
        return future;
    }

    private Future<JsonObject> bootstrapVerticles(JsonObject config) {
        Future<JsonObject> future = Future.future();
        future.complete(config);
        DeploymentOptions options = new DeploymentOptions().setConfig(config);
        vertx.deployVerticle(ImportVerticle.class.getName(), options);
        vertx.deployVerticle(TransformVerticle.class.getName(), options);
        vertx.deployVerticle(ExportVerticle.class.getName(), options);
        return future;
    }

    private void indexHandler(RoutingContext context) {
        JsonObject response = new JsonObject();
        response.put("status", "ok");
        context.response().setStatusCode(200);
        context.response().putHeader("Content-Type", "application/json");
        context.response().end(response.encode());
    }

    private void weatherHandler(RoutingContext context) {
        String bbox = context.request().getParam("bbox");
        JsonObject response = new JsonObject();
        context.response().putHeader("Content-Type", "application/json");
        if(bbox == null) {
            response.put("message", "Please provide a bounding box (bbox)");
            context.response().setStatusCode(400);
        } else {
            vertx.eventBus().send(Constants.MSG_IMPORT, bbox);
            response.put("status", "ok");
            context.response().setStatusCode(200);
        }
        context.response().end(response.encode());
    }
}
