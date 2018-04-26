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

        JsonObject message = new JsonObject();
        message.put("payload", context.getBodyAsJson().getString("payload"));

        vertx.eventBus().send("transform", message);

        context.response()
                .setStatusCode(202) // accepted
                .putHeader("Content-Type", "application/json")
                .end();
    }
}
