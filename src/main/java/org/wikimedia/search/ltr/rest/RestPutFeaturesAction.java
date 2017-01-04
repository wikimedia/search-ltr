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
import org.wikimedia.search.ltr.action.feature.put.PutFeaturesAction;
import org.wikimedia.search.ltr.action.feature.put.PutFeaturesRequest;
import org.wikimedia.search.ltr.action.feature.put.PutFeaturesResponse;

public class RestPutFeaturesAction extends BaseRestHandler {
    @Inject
    public RestPutFeaturesAction(Settings settings, RestController controller, Client client) {
        super(settings, controller, client);
        controller.registerHandler(PUT, "/_ltr/features", this);
    }

    @Override
    public void handleRequest(final RestRequest request, final RestChannel channel, final Client client)
        throws Exception {
        final PutFeaturesRequest putFeatureRequest = new PutFeaturesRequest();
        putFeatureRequest.timeout(request.paramAsTime("timeout", putFeatureRequest.timeout()));
        putFeatureRequest
            .masterNodeTimeout(request.paramAsTime("master_timeout", putFeatureRequest.masterNodeTimeout()))
            .features(request.content().toUtf8());

        client.execute(PutFeaturesAction.INSTANCE, putFeatureRequest,
            new AcknowledgedRestListener<PutFeaturesResponse>(channel));
    }
}
