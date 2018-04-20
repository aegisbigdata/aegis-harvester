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

    private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

    @Override
    public void start(Future<Void> future) {

        LOG.info("Launching transformer...");

        Future<Void> steps = loadConfig()
                .compose(this::bootstrapVerticle)
                .compose(this::startServer);

        steps.setHandler(handler -> {
            if (handler.succeeded()) {
                LOG.info("Transformer successfully launched");
            } else {
                LOG.error("Failed to launch transformer: " + handler.cause());
            }
        });
    }

    private Future<Void> startServer(JsonObject config) {
        Future<Void> future = Future.future();
        Integer port = config.getInteger("port");

        HttpServer server = vertx.createHttpServer();

        Router router = Router.router(vertx);
        router.get("/transform").handler(this::handleTransformation);

        server.requestHandler(router::accept).listen(port);
        LOG.info("Listening on port " + port.toString());

        return future;
    }

    private void handleTransformation(RoutingContext context) {
        String filePath = config().getString("fileDir")
                + context.getBodyAsJson().getString("pipeId");

        JsonObject message = new JsonObject();
        message.put("filePath", filePath);
        message.put("values", context.getBodyAsJson().getString("values"));

        vertx.eventBus().send("transform", message);

        context.response()
                .setStatusCode(202) // accepted
                .putHeader("Content-Type", "application/json")
                .end();
    }

    private Future<JsonObject> bootstrapVerticle(JsonObject config) {
        Future<JsonObject> future = Future.future();
        future.complete(config);

        DeploymentOptions options = new DeploymentOptions()
                .setConfig(config)
                .setWorker(true);

        vertx.deployVerticle(TransformationVerticle.class.getName(), options);

        return future;
    }

    private Future<JsonObject> loadConfig() {
        Future<JsonObject> future = Future.future();

        ConfigRetriever.create(vertx).getConfig(ar -> {
            if(ar.failed()) {
                future.failed();
            } else {
                future.complete(ar.result());
            }
        });

        return future;
    }
}
