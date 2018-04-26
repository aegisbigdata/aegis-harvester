import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MainVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

    private WebClient webClient;

    @Override
    public void start() {
        LOG.info("Launching aggregator...");

        webClient = WebClient.create(vertx);

        startServer().setHandler(handler -> {
            if (handler.succeeded()) {
                LOG.info("Aggregator successfully launched");
            } else {
                LOG.error("Failed to launch aggregator: " + handler.cause());
            }
        });
    }

    private Future<Void> startServer() {
        Future<Void> future = Future.future();
        Integer port = config().getInteger("port");

        Router router = Router.router(vertx);
        router.post("/aggregate").handler(this::aggregationHandler);

        vertx.createHttpServer().requestHandler(router::accept).listen(port);
        LOG.info("Listening on port " + port.toString());

        return future;
    }

    private void aggregationHandler(RoutingContext context) {
        JsonObject message = context.getBodyAsJson();

        String filePath = config().getString("fileDir")
                + message.getString("pipeId");

        vertx.fileSystem().exists(filePath, handler ->
                vertx.setTimer(config().getInteger("frequencyInMinutes") * 60000, timer -> {
                    exportFile(filePath);
                    cleanUp(filePath);
                }));

        String data = message.getString("payload");

        vertx.fileSystem().writeFile(filePath, Buffer.buffer(data), result -> {
            if (result.failed()) {
                LOG.error("Failed to write line [{}] to file: {}", data, filePath);
            }
        });
    }

    private void exportFile(String filePath) {
        JsonObject message = new JsonObject();
        message.put("pipeId", config().getString("pipeId"));
        message.put("payload", filePath);

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

    private void cleanUp(String filePath) {
        vertx.fileSystem().delete(filePath, handler -> {
            if (handler.failed())
                LOG.warn("Failed to clean up file [{}] : ", filePath, handler.cause());
        });
    }
}
