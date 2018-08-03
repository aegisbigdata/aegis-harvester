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

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AggregationVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(AggregationVerticle.class);

    private Map<String, String> fileNames;
    private Map<String, List<String>> buffer;

    private WebClient webClient;

    @Override
    public void start(Future<Void> future) {
        // concurrent map to prevent possible race conditions when timers end
        fileNames = new ConcurrentHashMap<>();
        buffer = new ConcurrentHashMap<>();

        webClient = WebClient.create(vertx);
        vertx.setPeriodic(10000, handler -> writeToFilePeriodically());

        vertx.eventBus().consumer(Constants.MSG_AGGREGATE, this::aggregate);
        future.complete();
    }

    private void aggregate(Message<String> message) {
        WriteRequest request = Json.decodeValue(message.body(), WriteRequest.class);
//        LOG.debug("Received {}", request.toString());

        String fileName = config().getString("fileDir") + "/"
                + new SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(new Date()).replace(" ", "T")
                + "_"
                + (!request.getBaseFileName().isEmpty()
                    ? request.getBaseFileName()
                    : UUID.randomUUID().toString())
                + ".csv";

        LOG.debug("Aggregating file [{}]", fileName);

        if (request.getAggregate()) {
            if (buffer.containsKey(request.getPipeId())) {
                buffer.get(request.getPipeId()).add(request.getCsvData());
            } else {
                fileNames.put(request.getPipeId(), fileName);

                List<String> data = new ArrayList<>();
                data.add(request.getCsvHeaders() + "\n");
                data.add(request.getCsvData());
                buffer.put(request.getPipeId(), data);

                vertx.setTimer(config().getInteger("frequencyInMinutes") * 60000, timer -> {
                    exportFile(request);
                    buffer.remove(request.getPipeId());
                    fileNames.remove(request.getPipeId());
                });
            }
        } else {
            fileNames.put(request.getPipeId(), fileName);
            String fileContent = request.getCsvHeaders() + "\n" + request.getCsvData();

            vertx.fileSystem().writeFile(fileName, Buffer.buffer(fileContent), handler -> {
                if (handler.succeeded()) {
                    exportFile(request);
                } else {
                    LOG.error("Failed to write to file [{}] : {}", fileName, handler.cause());
                }
            });
        }
    }

    private void writeToFilePeriodically() {
        buffer.forEach((pipeId, data) -> vertx.fileSystem().open(fileNames.get(pipeId), new OpenOptions().setCreate(true).setAppend(true), ar -> {
            if (ar.succeeded()) {
                AsyncFile ws = ar.result();

                data.forEach(line -> {
                    Buffer chunk = Buffer.buffer(line);
                    ws.write(chunk);
                });

                // clean up
                ws.close();
                buffer.get(pipeId).clear();
            } else {
                LOG.error("Could not open file [{}]", fileNames.get(pipeId));
            }
        }));
    }

    private void exportFile(WriteRequest request) {
        LOG.debug("Exporting file [{}]", fileNames.get(request.getPipeId()));

        JsonObject message = new JsonObject();
        message.put("pipeId", request.getPipeId());
        message.put("hopsProjectId", request.getHopsProjectId());
        message.put("hopsDataset", request.getHopsDataset());
        message.put("payload", fileNames.get(request.getPipeId()));

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
