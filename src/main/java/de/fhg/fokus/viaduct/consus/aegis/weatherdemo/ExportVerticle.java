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
        String projectId = config().getJsonObject("aegis").getString("projectId");
        String folder = config().getJsonObject("aegis").getString("folder");
        String url = config().getJsonObject("aegis").getString("url");  //test server
        String email = config().getJsonObject("aegis").getString("user");
        String password = config().getJsonObject("aegis").getString("password");
        HopsworksAdapter hopsworksAdapter = new HopsworksAdapter(email,password,url);
        hopsworksAdapter.actionUploadFile(projectId,folder,file);
    }
}
