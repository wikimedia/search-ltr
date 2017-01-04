package org.wikimedia.search.ltr.action.feature.put;

import org.elasticsearch.action.Action;
import org.elasticsearch.client.ElasticsearchClient;

public class PutFeaturesAction extends Action<PutFeaturesRequest, PutFeaturesResponse, PutFeaturesRequestBuilder> {
    public static final PutFeaturesAction INSTANCE = new PutFeaturesAction();
    public static final String NAME = "cluster:admin/ltr/features/put";

    private PutFeaturesAction() {
        super(NAME);
    }

    @Override
    public PutFeaturesResponse newResponse() {
        return new PutFeaturesResponse();
    }

    @Override
    public PutFeaturesRequestBuilder newRequestBuilder(ElasticsearchClient client) {
        return new PutFeaturesRequestBuilder(client, this);
    }
}
