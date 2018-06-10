import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import model.Constants;
import model.DataSendRequest;
import model.DataType;
import model.TransformationRequest;
import org.apache.commons.lang3.StringUtils;
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

            // prepare merge
            List<List<Integer>> columnsToMerge = new ArrayList<>();
            if (mapping.getJsonArray("mergeColumns") != null) {
                mapping.getJsonArray("mergeColumns").getList().forEach(setToMerge -> {

                    columnsToMerge.add((List<Integer>) setToMerge);
                });
            }

            // prepare convert
            List<Integer> columnsToConvert = new ArrayList<>();
            if (mapping.getJsonArray("convertTimeStamps") != null) {
                columnsToConvert = new ArrayList<>(mapping.getJsonArray("convertTimestamps").getList());
            }

            csv = transformCsv(lines.subList(1, lines.size()), columnsToMerge, columnsToConvert);

            // handle headers
            if (mapping.getJsonArray("renameHeaders") != null) {

                // extract rules for renaming headers
                Map<String, String> renameRules = new HashMap<>();
                mapping.getJsonArray("renameHeaders").iterator().forEachRemaining(r -> {
                    JsonObject rule = (JsonObject) r;
                    renameRules.put(rule.getString("old"), rule.getString("new"));
                });

                // add rules from merging columns
                // TODO

                headers = renameHeaders(Arrays.asList(lines.get(0).split(",")), renameRules);
            } else {
                headers = lines.get(0);
            }


        } else {
            headers = lines.get(0);
            csv = String.join("\n", lines.subList(1, lines.size()));
        }

        DataSendRequest sendRequest = new DataSendRequest(request.getPipeId(), request.getHopsFolder(), "localFile", headers, csv);
        vertx.eventBus().send(Constants.MSG_SEND, Json.encode(sendRequest));
    }

    private String transformCsv(List<String> rows, List<List<Integer>> columnsToMerge, List<Integer> columnsToConvert) {

        LOG.debug("Merging columns [{}]", Collections.singletonList(columnsToMerge));
        LOG.debug("Converting columns [{}]", Collections.singletonList(columnsToConvert));

        for (int i = 0; i < rows.size(); i++) {
            List<String> row = Arrays.asList(rows.get(i).split(","));
            row = removeQuotes(row);

            row = mergeColumns(row, columnsToMerge);
            row = convertDate(row, columnsToConvert);
            rows.set(i, String.join(",", row));
        }

        return String.join("\n", rows);
    }

    private String renameHeaders(List<String> headers, Map<String, String> renameRules) {
        LOG.debug("Renaming headers: [{}]", Collections.singletonList(renameRules));
        headers = removeQuotes(headers);

        for (Map.Entry<String, String> entry : renameRules.entrySet()) {
            for (int i = 0; i < headers.size(); i++) {
                if (entry.getKey().equals(headers.get(i)))
                    headers.set(i, entry.getValue());
            }
        }

        return String.join(",", headers);
    }

    // TODO rename headers
    private List<String> mergeColumns(List<String> row, List<List<Integer>> columnIndices) {
        // store columns to cut separately to prevent index errors when removing separately
        List<Integer> columnsToCut = new ArrayList<>();

        columnIndices.forEach(setToMerge -> {
            StringBuilder newVal = new StringBuilder();

            setToMerge.forEach(index -> newVal
                    .append(" ")
                    .append(row.get(index)));

            // set new value to first column to be merged
            row.set(setToMerge.get(0), newVal.toString());
            columnsToCut.addAll(setToMerge.subList(1, setToMerge.size()));
        });

        // remove all other columns
        return cutColumns(new ArrayList<>(row), columnsToCut);
    }

    private List<String> convertDate(List<String> row, List<Integer> columnIndicies) {
        //TODO convert to iso 8601
        columnIndicies.forEach(index -> row.set(index, "newDate"));
        return row;
    }

    private List<String> cutColumns(ArrayList<String> row, List<Integer> columnIndices) {

        // FIXME removing items from list in reverse order seems overly complicated
        ListIterator<String> rowIterator = row.listIterator(row.size());
        while (rowIterator.hasPrevious()) {
            if (columnIndices.contains(rowIterator.previousIndex())) {
                rowIterator.previous();
                rowIterator.remove();
            }

            rowIterator.previous();
        }

        return row;
    }

    private List<String> removeQuotes(List<String> row) {
        for (int i = 0; i < row.size(); i++) {
            String sanitized = StringUtils.removeStart(StringUtils.removeEnd(row.get(i), "\""), "\"");
            row.set(i, sanitized);
        }

        return row;
    }
}
