import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.Json;
import model.Constants;
import model.DataSendRequest;
import model.DataType;
import model.TransformationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;

public class CsvTransformationVerticle extends AbstractVerticle {


    private static final Logger LOG = LoggerFactory.getLogger(CsvTransformationVerticle.class);

    @Override
    public void start(Future<Void> future) {
        vertx.eventBus().consumer(DataType.CSV.getEventBusAddress(), this::handleTransformation);
        future.complete();
    }

    private void handleTransformation(Message<String> message) {
        TransformationRequest request = Json.decodeValue(message.body(), TransformationRequest.class);

        LOG.debug("Transforming {}", request.toString());

        // TODO make robust, allow custom transformations
        String newCsv = transformCsv(request.getPayload());

        DataSendRequest sendRequest = new DataSendRequest(request.getPipeId(), request.getHopsFolder(), "localFile", newCsv);
        vertx.eventBus().send(Constants.MSG_SEND, Json.encode(sendRequest));
    }

    private String transformCsv(String csv) {

        // TODO is this efficient?
        ArrayList<String> lines = new ArrayList<>(Arrays.asList(csv.split("\\R")));

        if (config().getBoolean("csv.stripHeader", false))
            lines.remove(0);

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            lines.set(i, line.substring(0, line.lastIndexOf(",")));
        }

        return String.join("\n", lines);
    }
}
