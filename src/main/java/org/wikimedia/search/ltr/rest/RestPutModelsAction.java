package org.wikimedia.search.ltr.rest;

import static org.elasticsearch.rest.RestRequest.Method.PUT;

import org.elasticsearch.client.Client;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.action.support.AcknowledgedRestListener;
import org.wikimedia.search.ltr.action.model.put.PutModelsAction;
import org.wikimedia.search.ltr.action.model.put.PutModelsRequest;
import org.wikimedia.search.ltr.action.model.put.PutModelsResponse;

public class RestPutModelsAction extends BaseRestHandler {
    @Inject
    public RestPutModelsAction(Settings settings, RestController controller, Client client) {
        super(settings, controller, client);
        controller.registerHandler(PUT, "/_ltr/models", this);
    }

    @Override
    public void handleRequest(final RestRequest request, final RestChannel channel, final Client client)
        throws Exception {
        final PutModelsRequest putFeatureRequest = new PutModelsRequest();
        putFeatureRequest.timeout(request.paramAsTime("timeout", putFeatureRequest.timeout()));
        putFeatureRequest
            .masterNodeTimeout(request.paramAsTime("master_timeout", putFeatureRequest.masterNodeTimeout()))
            .models(request.content().toUtf8());

        client.execute(PutModelsAction.INSTANCE, putFeatureRequest,
            new AcknowledgedRestListener<PutModelsResponse>(channel));
    }
}
