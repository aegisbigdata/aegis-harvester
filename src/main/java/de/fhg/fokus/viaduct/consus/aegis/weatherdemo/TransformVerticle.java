package de.fhg.fokus.viaduct.consus.aegis.weatherdemo;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.eventbus.Message;
import io.vertx.core.file.FileSystem;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Iterator;
import java.util.UUID;

public class TransformVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(TransformVerticle.class);

    @Override
    public void start(Future<Void> future) {
        LOGGER.info("Started TransformVerticle");
        vertx.eventBus().consumer(Constants.MSG_TRANSFORM, this::transform);
    }

    private void transform(Message<Object> message) {
        JsonArray list = (JsonArray)message.body();
        Iterator<Object> listItr = list.iterator();
        StringBuilder sb = new StringBuilder();
        sb.append("City,Time,Latitude,Longitude,Temperature\n");
        while(listItr.hasNext()) {
            JsonObject obj = (JsonObject)listItr.next();
            String city = obj.getString("name");
            String latitude = obj.getJsonObject("coord").getDouble("Lat").toString();
            String longitude = obj.getJsonObject("coord").getDouble("Lon").toString();
            String temp = obj.getJsonObject("main").getDouble("temp").toString();
            Long timeStamp = obj.getLong("dt");
            String date = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss").format(new Date(timeStamp * 1000L));
            sb.append(city).append(",");
            sb.append(date).append(",");
            sb.append(latitude).append(",");
            sb.append(longitude).append(",");
            sb.append(temp).append("\n");
        }
        String file = store(sb.toString());
        vertx.eventBus().send(Constants.MSG_EXPORT, file);
    }

    private String store(String data) {
        String id = UUID.randomUUID().toString();
        String tempFilePath = config().getString("tempFilePath");
        String file = tempFilePath + id + ".csv";
        FileSystem fs = vertx.fileSystem();
        fs.writeFile(file, Buffer.buffer(data), result -> {
            if (result.succeeded()) {
                LOGGER.info("Written file " + file);
            } else {
                LOGGER.error("Oh oh ..." + result.cause());
            }
        });
        return file;
    }
}

