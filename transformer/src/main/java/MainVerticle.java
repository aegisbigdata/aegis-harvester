import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.json.DecodeException;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import model.Constants;
import model.TransformationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

    private JsonObject config;

    @Override
    public void start(Future<Void> future) {

        LOG.info("Launching transformer...");

        Future<Void> steps = loadConfig()
                .compose(handler -> bootstrapVerticle())
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
        Future<Void> future = Future.future();

        ConfigRetriever.create(vertx).getConfig(handler -> {
            if(handler.succeeded()) {
                config = handler.result();
                future.complete();
            } else {
                future.fail("Failed to load config: " + handler.cause());
            }
        });

        return future;
    }

    private Future<Void> bootstrapVerticle() {
        Future<Void> future = Future.future();

        DeploymentOptions options = new DeploymentOptions()
                .setConfig(config)
                .setWorker(true);

        vertx.deployVerticle(TransformationVerticle.class.getName(), options, handler -> {
            if (handler.succeeded()) {
                future.complete();
            } else {
                future.fail("Failed to deploy transformation verticle: " + handler.cause());
            }
        });

        return future;
    }

    private Future<Void> startServer() {
        Future<Void> future = Future.future();
        Integer port = config.getInteger("http.port");

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.post("/transform").handler(this::handleTransformation);

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

    private void handleTransformation(RoutingContext context) {
        try {
            LOG.debug("Received request with body {}", context.getBodyAsString());

            Json.decodeValue(context.getBody().toString(), TransformationRequest.class);
            vertx.eventBus().send(Constants.MSG_TRANSFORM, context.getBodyAsString());

            context.response()
                    .setStatusCode(202) // accepted
                    .putHeader("Content-Type", "application/json")
                    .end();
        } catch (DecodeException e) {
            e.printStackTrace();
            LOG.debug("Invalid request received");
            context.response()
                    .setStatusCode(400)
                    .end("Invalid JSON provided");
        }
    }
}
