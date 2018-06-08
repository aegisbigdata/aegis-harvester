import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import model.Constants;
import model.DataSendRequest;
import model.DataType;
import model.TransformationRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

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

        JsonObject payload = new JsonObject(request.getPayload());

        // FIXME is this efficient?
        ArrayList<String> lines = new ArrayList<>(Arrays.asList(payload.getString("csv").split("\\R")));
        String headers;
        String csv;

        JsonObject mapping = payload.getJsonObject("mapping");
        if (mapping != null) {
            LOG.debug("Mapping loaded: {}", mapping.toString());

            if (mapping.getJsonArray("renameHeaders") != null) {

                // extract rules for renaming headers
                Map<String, String> renameRules = new HashMap<>();
                mapping.getJsonArray("renameHeaders").iterator().forEachRemaining(r -> {
                    JsonObject rule = (JsonObject) r;
                    renameRules.put(rule.getString("old"), rule.getString("new"));
                });

                headers = renameHeaders(Arrays.asList(lines.get(0).split(",")), renameRules);
            } else {
                headers = lines.get(0);
            }

            csv = transformCsv(lines, payload.getJsonObject("mapping"));
        } else {
            headers = lines.get(0);
            csv = String.join("\n", lines.subList(1, lines.size()));
        }

        DataSendRequest sendRequest = new DataSendRequest(request.getPipeId(), request.getHopsFolder(), "localFile", csv, headers);
        vertx.eventBus().send(Constants.MSG_SEND, Json.encode(sendRequest));
    }

    private String transformCsv(ArrayList<String> rows, JsonObject mapping) {

        // prepare merge
        List<List<Integer>> columnsToMerge = new ArrayList<>();
        if (mapping.getJsonArray("mergeColumns") != null) {
            mapping.getJsonArray("mergeColumns").getList().forEach(setToMerge -> {
                columnsToMerge.add((List<Integer>) setToMerge);
            });
        }
        LOG.debug("Merging columns [{}]", Collections.singletonList(columnsToMerge));

        // prepare convert
        List<Integer> columnsToConvert = new ArrayList<>();
        if (mapping.getJsonArray("convertTimeStamps") != null) {
            columnsToConvert = new ArrayList<>(mapping.getJsonArray("convertTimestamps").getList());
        }
        LOG.debug("Converting columns [{}]", Collections.singletonList(columnsToConvert));

        for (int i = 0; i < rows.size(); i++) {
            List<String> row = Arrays.asList(rows.get(i).split(","));

            row = mergeColumns(row, columnsToMerge);
            row = convertDate(row, columnsToConvert);
            rows.set(i, String.join(",", row));
        }

        return String.join("\n", rows);
    }

    private String renameHeaders(List<String> headers, Map<String, String> renameRules) {
        LOG.debug("Renaming headers: [{}]", Collections.singletonList(renameRules));

        renameRules.forEach((oldName, newName) -> {
            for (int i = 0; i < headers.size(); i++) {
                LOG.debug("Header: [{}]     Old: [{}]    New: [{}]", headers.get(i), oldName, newName);
                if (oldName.equals(headers.get(i)))
                    headers.set(i, newName);
            }
        });

        return String.join(",", headers);
    }

    private List<String> mergeColumns(List<String> row, List<List<Integer>> columnIndices) {
        // store columns to cut separately to prevent index errors when removing separately
        List<Integer> columnsToCut = new ArrayList<>();

        columnIndices.forEach(setToMerge -> {
            StringBuilder newVal = new StringBuilder();

            setToMerge.forEach(index -> newVal.append(row.get(index)));

            // set new value to first column to be merged
            row.set(setToMerge.get(0), newVal.toString());
            columnsToCut.addAll(setToMerge.subList(1, setToMerge.size()));
        });

        // remove all other columns
        return cutColumns(row, columnsToCut);
    }

    private List<String> convertDate(List<String> row, List<Integer> columnIndicies) {
        //TODO convert to iso 8601
        columnIndicies.forEach(index -> row.set(index, "newDate"));
        return row;
    }

    private List<String> cutColumns(List<String> row, List<Integer> columnIndices) {
        // iterate in reverse order to prevent index out of bounds errors
        ListIterator<Integer> reverseIterator = columnIndices.listIterator(columnIndices.size());
        reverseIterator.forEachRemaining(index -> row.remove(index.intValue()));
        return row;
    }
}
