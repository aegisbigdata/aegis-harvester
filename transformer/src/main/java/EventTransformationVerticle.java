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

import java.util.ArrayList;
import java.util.List;

public class EventTransformationVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

    private static final String CSV_HEADERS = "id,text,tweet_id,is_retweet,account,account_location,account_friends," +
            "account_followers,text_clean,created_date,category,keywords_text,text_location,text_datetime,location_lat," +
            "location_lon,has_location,has_datetime,has_numbers,has_mentions,num_words,score,retrieved_date";

    @Override
    public void start(Future<Void> future) {
        vertx.eventBus().consumer(DataType.EVENT.getEventBusAddress(), this::handleCustomTransformation);

        future.complete();
    }

    private void handleCustomTransformation(Message<String> message) {
        TransformationRequest request = Json.decodeValue(message.body(), TransformationRequest.class);
        LOG.debug("Transforming {}", request);

        JsonObject payload = new JsonObject(request.getPayload());
        List<String> csvValues = new ArrayList<>();

        String id = payload.getString("id");
        csvValues.add(id != null ? id : "");

        String text = payload.getString("text");
        csvValues.add(text != null ? text : "");

        String tweetId = payload.getString("tweet_id");
        csvValues.add(tweetId != null ? tweetId : "");

        Boolean isRetweet = payload.getBoolean("is_retweet");
        csvValues.add(isRetweet != null ? isRetweet.toString() : "");

        String account = payload.getString("account");
        csvValues.add(account != null ? account : "");

        String accountLocation = payload.getString("account_location");
        csvValues.add(accountLocation != null ? accountLocation : "");

        Integer accountFriends = payload.getInteger("account_friends");
        csvValues.add(accountFriends != null ? accountFriends.toString() : "");

        Integer accountFollowers = payload.getInteger("account_followers");
        csvValues.add(accountFollowers != null ? accountFollowers.toString() : "");

        String textClean = payload.getString("text_clean");
        csvValues.add(textClean != null ? textClean : "");

        String createdDate = payload.getString("created_date");
        csvValues.add(createdDate != null ? createdDate : "");

        String category = payload.getString("category");
        csvValues.add(category != null ? category : "");

        JsonArray keywordsText = payload.getJsonArray("keywords_text");
        StringBuilder sb = new StringBuilder();
        keywordsText.forEach(keyword -> sb.append((String) keyword).append(","));
        csvValues.add(sb.toString().substring(0, sb.length() - 1));

        String textLocation = payload.getString("text_location");
        csvValues.add(textLocation != null ? textLocation : "");

        String textDateTime = payload.getString("text_datetime");
        csvValues.add(textDateTime != null ? textDateTime : "");

        Float locationLat = payload.getFloat("location_lat");
        csvValues.add(locationLat != null ? locationLat.toString() : "");

        Float locationLon = payload.getFloat("location_lon");
        csvValues.add(locationLon != null ? locationLon.toString() : "");

        Boolean hasLocation = payload.getBoolean("has_location");
        csvValues.add(hasLocation != null ? hasLocation.toString() : "");

        Boolean hasDateTime = payload.getBoolean("has_datetime");
        csvValues.add(hasDateTime != null ? hasDateTime.toString() : "");

        Boolean hasNumbers = payload.getBoolean("has_numbers");
        csvValues.add(hasNumbers != null ? hasNumbers.toString() : "");

        Integer numMentions = payload.getInteger("num_mentions");
        csvValues.add(numMentions != null ? numMentions.toString() : "");

        Integer numWords = payload.getInteger("num_words");
        csvValues.add(numWords != null ? numWords.toString() : "");

        Float score = payload.getFloat("score");
        csvValues.add(score != null ? score.toString() : "");

        String retrievedDate = payload.getString("retrieved_date");
        csvValues.add(retrievedDate != null ? retrievedDate : "");


        for (int i = 0; i < csvValues.size(); i++)
            csvValues.set(i, escapeIfRequired(csvValues.get(i)));

        String csv = String.join(",", csvValues) + "\n";

        DataSendRequest sendRequest =
                new DataSendRequest(request.getPipeId(), request.getHopsProjectId(), request.getHopsDataset(), request.getBaseFileName(), CSV_HEADERS, csv, true, request.getUser(), request.getPassword(), request.getMetadata());

        vertx.eventBus().send(Constants.MSG_SEND, Json.encode(sendRequest));
    }

    private String escapeIfRequired(String csvValue) {
        return !csvValue.contains(",")
                ? csvValue
                : "\"" + csvValue.replaceAll("\"", "\"\"") + "\"";
    }
}
