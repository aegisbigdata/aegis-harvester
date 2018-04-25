import io.vertx.core.AbstractVerticle;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

    @Override
    public void start(Future<Void> future) {

        LOG.info("Launching transformer...");

        Future<Void> steps = bootstrapVerticle()
                .compose(abc -> startServer());

        steps.setHandler(handler -> {
            if (handler.succeeded()) {
                LOG.info("Transformer successfully launched");
            } else {
                LOG.error("Failed to launch transformer: " + handler.cause());
            }
        });
    }

    private Future<Void> bootstrapVerticle() {
        Future<Void> future = Future.future();

        DeploymentOptions options = new DeploymentOptions()
                .setConfig(config())
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

        Integer port = config().getInteger("http.port");
        HttpServer server = vertx.createHttpServer();

        Router router = Router.router(vertx);
        router.get("/transform").handler(this::handleTransformation);

        server.requestHandler(router::accept)
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
        String filePath = config().getString("fileDir")
                + context.getBodyAsJson().getString("pipeId");

        // notify target on creation of a new file
        vertx.fileSystem().exists(filePath, handler -> {
            if (handler.succeeded() && !handler.result()) {
                notifyTargetOfNewFile(filePath);
            } else if (handler.failed()) {
                LOG.warn("Failed to check if file [{}] existst: {}", filePath, handler.cause());
            }
        });

        JsonObject message = new JsonObject();
        message.put("filePath", filePath);
        message.put("values", context.getBodyAsJson().getString("values"));

        vertx.eventBus().send("transform", message);

        context.response()
                .setStatusCode(202) // accepted
                .putHeader("Content-Type", "application/json")
                .end();
    }

    private void notifyTargetOfNewFile(String filePath) {
        JsonObject message = new JsonObject();
        message.put("pipeId", config().getString("pipeId"));
        message.put("filePath", filePath);

        Integer port = config().getInteger("target.port");
        String host = config().getString("target.host");
        String requestURI = config().getString("target.endpoint");

        WebClient.create(vertx)
                .post(port, host, requestURI)
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
