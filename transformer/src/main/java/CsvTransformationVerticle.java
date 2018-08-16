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

    private static final String DEFAULT_FILE_NAME = "localfile";

    @Override
    public void start(Future<Void> future) {
        vertx.eventBus().consumer(DataType.CSV.getEventBusAddress(), this::handleTransformation);
        future.complete();
    }

    private void handleTransformation(Message<String> message) {
        TransformationRequest request = Json.decodeValue(message.body(), TransformationRequest.class);

        JsonObject payload = new JsonObject(request.getPayload());

        // FIXME is this efficient?
        ArrayList<String> lines = new ArrayList<>(Arrays.asList(payload.getString("csv").split("\\R")));

        CsvFile csvFile =
                new CsvFile(request.getBaseFileName(), Arrays.asList(lines.get(0).split(",")), lines.subList(1, lines.size()), payload.getJsonObject("mapping"));

        List<CsvFile> csv;

        if (csvFile.getMapping() != null) {
            LOG.debug("Mapping loaded: {}", csvFile.getMapping().toString());
            csv = transformCsv(csvFile);
        } else {
            csv = Collections.singletonList(csvFile);
        }

        csv.forEach(file -> {
            String headers = String.join(",", file.getHeaders());
            String content = String.join("\n", file.getContent());

            DataSendRequest sendRequest =
                    new DataSendRequest(request.getPipeId(), request.getHopsProjectId(), request.getHopsDataset(), file.getFileName(), headers, content, false, request.getUser(), request.getPassword());

            vertx.eventBus().send(Constants.MSG_SEND, Json.encode(sendRequest));
        });
    }


    private List<CsvFile> transformCsv(CsvFile csvFile) {

        JsonObject mapping = csvFile.getMapping();

        // prepare renaming of headers
        Map<String, String> renameRules = new HashMap<>();
        if (mapping.getJsonArray("renameHeaders") != null) {
            mapping.getJsonArray("renameHeaders").iterator().forEachRemaining(r -> {
                JsonObject rule = (JsonObject) r;
                renameRules.put(rule.getString("old"), rule.getString("new"));
            });
        }
        LOG.debug("Renaming headers: [{}]", Collections.singletonList(renameRules));


        // prepare switch of columns
        List<List<Integer>> columnsToSwitch = new ArrayList<>();
        if (mapping.getJsonArray("switchColumns") != null) {
            mapping.getJsonArray("switchColumns").getList().forEach(setToSwitch ->
                    columnsToSwitch.add((List<Integer>) setToSwitch));
        }
        LOG.debug("Switching columns [{}]", Collections.singletonList(columnsToSwitch));


        // prepare merging of columns
        List<List<Integer>> columnsToMerge = new ArrayList<>();
        if (mapping.getJsonArray("mergeColumns") != null) {
            mapping.getJsonArray("mergeColumns").getList().forEach(setToMerge ->
                    columnsToMerge.add((List<Integer>) setToMerge));
        }
        LOG.debug("Merging columns [{}]", Collections.singletonList(columnsToMerge));

        // compute columns to be removed
        List<Integer> columnsToCut = new ArrayList<>();
        columnsToMerge.forEach(setToMerge ->
                columnsToCut.addAll(setToMerge.subList(1, setToMerge.size()))
        );

        // prepare converting of timestamps
        List<Integer> columnsToConvert = new ArrayList<>();
        if (mapping.getJsonArray("convertTimeStamps") != null) {
            columnsToConvert = new ArrayList<>(mapping.getJsonArray("convertTimeStamps").getList());
        }
        LOG.debug("Converting columns [{}]", Collections.singletonList(columnsToConvert));


        // transform headers
        List<String> headers = removeQuotes(csvFile.getHeaders());
        csvFile.setHeaders(
                applyQuotes(
                        cutColumns(
                                new ArrayList<>(switchColumns(
                                        renameHeaders(headers, renameRules),
                                        columnsToSwitch)),
                                columnsToCut
                        )));


        // transform csv content
        for (int i = 0; i < csvFile.getContent().size(); i++) {

            List<String> row = Arrays.asList(csvFile.getContent().get(i).split(","));

            row = applyQuotes(cutColumns(
                            new ArrayList<>(mergeColumns(
                                    switchColumns(
                                            convertDate(removeQuotes(row), columnsToConvert),
                                            columnsToSwitch),
                                    columnsToMerge)),
                    columnsToCut));

            csvFile.getContent().set(i, String.join(",", row));
        }

        return splitCsv(csvFile);
    }

    private List<String> renameHeaders(List<String> headers, Map<String, String> renameRules) {
        for (Map.Entry<String, String> entry : renameRules.entrySet()) {
            for (int i = 0; i < headers.size(); i++) {
                if (entry.getKey().equals(headers.get(i)))
                    headers.set(i, entry.getValue());
            }
        }

        return headers;
    }

    private List<String> switchColumns(List<String> row, List<List<Integer>> columnIndices) {
        columnIndices.forEach(pairToSwitch -> {
            if (pairToSwitch.size() == 2) {
                String tmpValue = row.get(pairToSwitch.get(0));
                row.set(pairToSwitch.get(0), row.get(pairToSwitch.get(1)));
                row.set(pairToSwitch.get(1), tmpValue);
            } else {
                LOG.error("Switching rows requires exactly two columns to be specified");
            }
        });

        return row;
    }

    private List<String> mergeColumns(List<String> row, List<List<Integer>> columnIndices) {
        columnIndices.forEach(setToMerge -> {
            StringBuilder newVal = new StringBuilder();

            setToMerge.forEach(index -> newVal
                    .append(", ")
                    .append(row.get(index)));

            // remove leading whitespace and set new value to first column to be merged
            row.set(setToMerge.get(0), StringUtils.removeStart(newVal.toString(), ", "));
        });

        // remove all other columns
        return new ArrayList<>(row);
    }

    private List<String> convertDate(List<String> row, List<Integer> columnIndicies) {
        //TODO convert to iso 8601
//        columnIndicies.forEach(index -> row.set(index, "newDate"));
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

    private List<String> applyQuotes(List<String> row) {
        for (int i = 0; i < row.size(); i++) {
            String quoted = "\"" + row.get(i) + "\"";
            row.set(i, quoted);
        }

        return row;
    }

    // splits csv by differing values in a provided column specified by its index
    private List<CsvFile> splitCsv(CsvFile csvFile) {

        Integer column = csvFile.getMapping().getInteger("splitByColumn");

        if (column != null && column >= 0 && column < csvFile.getContent().get(0).split(",").length) {

            Map<String, CsvFile> filesWithValues = new HashMap<>();
            String baseFileName = removeQuotes(csvFile.getHeaders()).get(column) + "_";

            csvFile.getContent().forEach(row -> {
                List<String> columnValues = removeQuotes(Arrays.asList(row.split(",")));

                String fileName = baseFileName + columnValues.get(column);

                CsvFile existingCsv = filesWithValues.get(fileName);

                if (existingCsv != null) {
                    existingCsv.getContent().add(row);
                } else {
                    List<String> newContent = new ArrayList<>(Collections.singletonList(row));
                    CsvFile newFile = new CsvFile(fileName, csvFile.getHeaders(), newContent, csvFile.getMapping());
                    filesWithValues.put(fileName, newFile);
                }
            });

            return new ArrayList<>(filesWithValues.values());
        } else {
            LOG.error("Column with index [{}] does not exist, returning entire file", column);
            return new ArrayList<>(Collections.singletonList(csvFile));
        }
    }

    private class CsvFile {
        private String fileName;
        private List<String> headers;
        private List<String> content;
        private JsonObject mapping;

        public CsvFile(String fileName, List<String> headers, List<String> content, JsonObject mapping) {
            this.fileName = fileName;
            this.headers = headers;
            this.content = content;
            this.mapping = mapping;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public List<String> getHeaders() {
            return headers;
        }

        public void setHeaders(List<String> headers) {
            this.headers = headers;
        }

        public List<String> getContent() {
            return content;
        }

        public void setContent(List<String> content) {
            this.content = content;
        }

        public JsonObject getMapping() {
            return mapping;
        }

        public void setMapping(JsonObject mapping) {
            this.mapping = mapping;
        }
    }
}
