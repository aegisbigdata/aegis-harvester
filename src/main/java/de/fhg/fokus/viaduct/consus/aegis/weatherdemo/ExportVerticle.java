package de.fhg.fokus.viaduct.consus.aegis.weatherdemo;

import de.fokus.fraunhofer.hopsworks.adapter.HopsworksAdapter;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExportVerticle extends AbstractVerticle {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExportVerticle.class);

    @Override
    public void start(Future<Void> future) {
        LOGGER.info("Started ExportVerticle");
        vertx.eventBus().consumer(Constants.MSG_EXPORT, this::export);
    }

    private void export(Message<Object> message) {
        String file = message.body().toString();
        String projectID = "1027";
        String folder = "upload/test_fki";
        HopsworksAdapter hopsworksAdapter = new HopsworksAdapter();
        hopsworksAdapter.actionUploadFile(projectID, folder, file);
    }
}
