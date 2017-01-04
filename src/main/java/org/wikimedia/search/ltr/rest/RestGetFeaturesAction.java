package org.wikimedia.search.ltr.rest;

import static org.elasticsearch.rest.RestRequest.Method.GET;

import org.elasticsearch.action.admin.cluster.state.ClusterStateRequest;
import org.elasticsearch.action.admin.cluster.state.ClusterStateResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.Requests;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestChannel;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestResponse;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.rest.action.support.RestBuilderListener;
import org.wikimedia.search.ltr.store.FeaturesState;

public class RestGetFeaturesAction extends BaseRestHandler {

    @Inject
    public RestGetFeaturesAction(Settings settings, RestController controller, Client client) {
        super(settings, controller, client);
        controller.registerHandler(GET, "/_ltr/features", this);
    }

    @Override
    public void handleRequest(final RestRequest request, final RestChannel channel, final Client client) {
        ClusterStateRequest clusterStateRequest = Requests.clusterStateRequest().routingTable(false).nodes(false)
            .customs(true).metaData(false);
        clusterStateRequest.local(request.paramAsBoolean("local", clusterStateRequest.local()));
        client.admin().cluster().state(clusterStateRequest, new RestBuilderListener<ClusterStateResponse>(channel) {
            @Override
            public RestResponse buildResponse(ClusterStateResponse response, XContentBuilder builder) throws Exception {
                builder.startObject();
                builder.startObject("features");
                FeaturesState featuresState = response.getState().<FeaturesState>custom(FeaturesState.TYPE);
                if (featuresState != null) {
                    featuresState.toXContent(builder, ToXContent.EMPTY_PARAMS);
                }
                builder.endObject();
                builder.endObject();
                return new BytesRestResponse(RestStatus.OK, builder);
            }
        });

    }
}
