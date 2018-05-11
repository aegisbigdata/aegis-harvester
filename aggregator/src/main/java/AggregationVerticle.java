import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.file.AsyncFile;
import io.vertx.core.file.OpenOptions;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.HttpResponse;
import io.vertx.ext.web.client.WebClient;
import model.Constants;
import model.WriteRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AggregationVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(AggregationVerticle.class);

    private Set<String> aggregationIds;
    private Map<String, List<String>> buffer;

    private WebClient webClient;

    @Override
    public void start(Future<Void> future) {
        // concurrent map to prevent possible race conditions when timers end
        aggregationIds = ConcurrentHashMap.newKeySet();
        buffer = new ConcurrentHashMap<>();

        webClient = WebClient.create(vertx);
        vertx.setPeriodic(10000, handler -> writeToFilePeriodically());

        vertx.eventBus().consumer(Constants.MSG_DATA, this::aggregate);
        future.complete();
    }

    private void aggregate(Message<String> message) {
        WriteRequest request = Json.decodeValue(message.body(), WriteRequest.class);
        LOG.debug("Received aggregation request for {}", request);

        if (buffer.containsKey(request.getFilePath())) {
            buffer.get(request.getFilePath()).add(request.getData());
        } else {
            List<String> data = new ArrayList<>();
            data.add(request.getData());
            buffer.put(request.getFilePath(), data);
        }

        // create a new timer when none has been created, remove timer ID when timer fires
        vertx.fileSystem().exists(request.getFilePath(), handler -> {
            if (handler.succeeded()) {
                LOG.debug("Timers currently running: {}", aggregationIds);
                if (!aggregationIds.contains(request.getPipeId())) {
                    vertx.setTimer(config().getInteger("frequencyInMinutes") * 60000, timer -> {
                        exportFile(request.getPipeId(), request.getFilePath());
                        aggregationIds.remove(request.getPipeId());
                    });

                    aggregationIds.add(request.getPipeId());
                }
            } else {
                LOG.error("Failed to set export timer for pipe with ID [{}]", request.getPipeId());
            }
        });
    }

    private void writeToFilePeriodically() {
        buffer.forEach((filePath, data) -> vertx.fileSystem().open(filePath, new OpenOptions().setAppend(true), ar -> {
            if (ar.succeeded()) {
                AsyncFile ws = ar.result();

                data.forEach(line -> {
                    Buffer chunk = Buffer.buffer(line);
                    ws.write(chunk);
                });

                // remove data already written
                buffer.get(filePath).clear();
            } else {
                LOG.error("Could not open file [{}]", filePath);
            }
        }));
    }

    private void exportFile(String pipeId, String filePath) {
        LOG.debug("Exporting file [{}]", filePath);

        JsonObject message = new JsonObject();
        message.put("pipeId", pipeId);
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
}
