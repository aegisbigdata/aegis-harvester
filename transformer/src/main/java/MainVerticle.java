import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import model.DataType;
import model.TransformationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class MainVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

    private JsonObject config;

    @Override
    public void start(Future<Void> future) {

        LOG.info("Launching transformer...");

        Future<Void> steps = loadConfig()
                .compose(handler -> bootstrapVerticles())
                .compose(handler -> startServer());

        steps.setHandler(handler -> {
            if (handler.succeeded()) {
                LOG.info("Transformer successfully launched");
            } else {
                LOG.error("Failed to launch transformer: " + handler.cause());
            }
        });
    }

    private Future<Void> loadConfig() {
        LOG.info("Loading config...");

        Future<Void> future = Future.future();

        ConfigRetriever.create(vertx).getConfig(handler -> {
            if (handler.succeeded()) {
                LOG.info("Config successfully loaded");
                config = handler.result();
                future.complete();
            } else {
                future.fail("Failed to load config: " + handler.cause());
            }
        });

        return future;
    }

    private CompositeFuture bootstrapVerticles() {

        DeploymentOptions options = new DeploymentOptions()
                .setConfig(config)
                .setWorker(true);

        List<Future> deploymentFutures = new ArrayList<>();
        deploymentFutures.add(startVerticle(options, OwmTransformationVerticle.class.getName()));
        deploymentFutures.add(startVerticle(options, CsvTransformationVerticle.class.getName()));
        deploymentFutures.add(startVerticle(options, EventTransformationVerticle.class.getName()));
        deploymentFutures.add(startVerticle(options, DataSenderVerticle.class.getName()));

        return CompositeFuture.join(deploymentFutures);
    }

    private Future<Void> startVerticle(DeploymentOptions options, String className) {
        LOG.info("Deploying verticle: {}", className);

        Future<Void> future = Future.future();

        vertx.deployVerticle(className, options, handler -> {
            if (handler.succeeded()) {
                LOG.info("Successfully Deployed verticle: {}", className);
                future.complete();
            } else {
                future.fail("Failed to deploy : " + className + ", cause : " + handler.cause());
            }
        });

        return future;
    }

    private Future<Void> startServer() {
        LOG.info("Starting server...");

        Future<Void> future = Future.future();
        Integer port = config.getInteger("http.port");

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.post("/transform").handler(this::dispatchRequest);

        vertx.createHttpServer().requestHandler(router::accept)
                .listen(port, handler -> {
                    if (handler.succeeded()) {
                        future.complete();
                        LOG.info("Listening on port " + port.toString());
                    } else {
                        future.fail("Failed to start server: " + handler.cause());
                    }
                });

        return future;
    }

    private void dispatchRequest(RoutingContext context) {
        try {
            //LOG.debug("Received request with body {}", context.getBodyAsString());

            TransformationRequest request = Json.decodeValue(context.getBodyAsString(), TransformationRequest.class);
            HttpServerResponse response = context.response();

            switch (request.getDataType()) {
                case OWM:
                    LOG.info("Received request: OWM");
                    vertx.eventBus().send(DataType.OWM.getEventBusAddress(), context.getBodyAsString());
                    response.setStatusCode(202);
                    break;
                case CSV:
                    LOG.info("Received request: CSV");
                    vertx.eventBus().send(DataType.CSV.getEventBusAddress(), context.getBodyAsString());
                    response.setStatusCode(202);
                    break;
                case EVENT:
                    LOG.info("Received request: EVENT");
                    vertx.eventBus().send(DataType.EVENT.getEventBusAddress(), context.getBodyAsString());
                    response.setStatusCode(202);
                    break;
                default:
                    // should never happen
                    LOG.error("Invalid data type provided");
                    response.setStatusCode(400);
                    break;
            }

            response.putHeader("Content-Type", "application/json")
                    .end();

        } catch (DecodeException e) {
            LOG.debug("Invalid request received");
            context.response()
                    .setStatusCode(400)
                    .end("Invalid JSON provided");
        }
    }
}
