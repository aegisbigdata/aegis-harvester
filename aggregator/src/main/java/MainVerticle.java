import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.handler.BodyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class MainVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

    private WebClient webClient;

    private Set<String> aggregationIds;

    @Override
    public void start() {
        LOG.info("Launching aggregator...");

        webClient = WebClient.create(vertx);

        // concurrent map to prevent possible race conditions when timers end
        aggregationIds = ConcurrentHashMap.newKeySet();

        Future<Void> steps = loadConfig()
                .compose(this::startServer);

        steps.setHandler(handler -> {
            if (handler.succeeded()) {
                LOG.info("Aggregator successfully launched");
            } else {
                handler.cause().printStackTrace();
                LOG.error("Failed to launch aggregator: " + handler.cause());
            }
        });
    }

    private Future<JsonObject> loadConfig() {
        Future<JsonObject> future = Future.future();

        ConfigRetriever.create(vertx).getConfig(handler -> {
            if (handler.succeeded()) {
                future.complete(handler.result());
            } else {
                future.fail("Failed to load config: " + handler.cause());
            }
        });

        return future;
    }

    private Future<Void> startServer(JsonObject config) {
        Future<Void> future = Future.future();
        Integer port = config.getInteger("http.port");

        Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.post("/aggregate").handler(context ->
                aggregationHandler(context, config)
        );

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

    private void aggregationHandler(RoutingContext context, JsonObject config) {
        LOG.debug("Received request with body {}", context.getBodyAsString());
        JsonObject message = context.getBodyAsJson();

        String pipeId = message.getString("pipeId");
        String filePath = config.getString("fileDir") + "/"
                + pipeId;

        // create a new timer when none has been created, remove timer ID when timer fires
        vertx.fileSystem().exists(filePath, handler -> {
            if (handler.succeeded()) {
                LOG.debug("Timers currently running: {}", aggregationIds);
                if (!aggregationIds.contains(pipeId)) {
                    vertx.setTimer(config.getInteger("frequencyInMinutes") * 60000, timer -> {
                        exportFile(pipeId, filePath, config);
                        aggregationIds.remove(pipeId);
                    });

                    aggregationIds.add(pipeId);
                }
            } else {
                LOG.error("Failed to set export timer for pipe with ID [{}]", pipeId);
            }
        });

        String data = message.getString("payload");
        tryWriteToFile(filePath, data, 3);
    }

    private void exportFile(String pipeId, String filePath, JsonObject config) {
        JsonObject message = new JsonObject();
        message.put("pipeId", pipeId);
        message.put("payload", filePath);

        Integer port = config.getInteger("target.port");
        String host = config.getString("target.host");
        String requestURI = config.getString("target.endpoint");

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

    private void tryWriteToFile(String filePath, String data, int numberOfTries) {
        String lockFile = filePath + ".lock";

        if (numberOfTries > 0) {
            // check if lock file exists
            vertx.fileSystem().exists(lockFile, existsHandler -> {
                if (!existsHandler.result()) {
                    // create lock file
                    vertx.fileSystem().createFile(lockFile, lockFileHandler -> {
                        if (lockFileHandler.succeeded()) {
                            // write to actual file
                            vertx.fileSystem().open(filePath, new OpenOptions().setAppend(true), ar -> {
                                if (ar.succeeded()) {
                                    AsyncFile ws = ar.result();
                                    Buffer chunk = Buffer.buffer(data);
                                    ws.write(chunk);
                                    // delete lock file
                                    vertx.fileSystem().delete(lockFile, deleteLockFileHandler -> {
                                        if (deleteLockFileHandler.failed())
                                            LOG.error("Failed to delete lock file [{}]", lockFile);
                                    });
                                } else {
                                    LOG.error("Could not open file [{}]", filePath);
                                }
                            });
                        } else {
                            LOG.error("Failed to create lock file");
                        }
                    });
                } else {
                    try {
                        Thread.sleep(5000);
                        tryWriteToFile(filePath, data, numberOfTries - 1);
                    } catch (InterruptedException e) {
                        LOG.error("Waiting for retry threw an exception: {}", e.getMessage());
                    }
                }
            });
        } else {
            LOG.error("Could not write to file [{}]", filePath);
        }
    }
}
