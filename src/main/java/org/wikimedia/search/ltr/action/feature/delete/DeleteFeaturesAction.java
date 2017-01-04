package org.wikimedia.search.ltr.action.feature.delete;

import org.elasticsearch.action.Action;
import org.elasticsearch.client.ElasticsearchClient;

public class DeleteFeaturesAction extends Action<DeleteFeaturesRequest, DeleteFeaturesResponse, DeleteFeaturesRequestBuilder> {
    public static final DeleteFeaturesAction INSTANCE = new DeleteFeaturesAction();
    public static final String NAME = "cluster:admin/ltr/features/delete";

    private DeleteFeaturesAction() {
        super(NAME);
    }

    @Override
    public DeleteFeaturesResponse newResponse() {
        return new DeleteFeaturesResponse();
    }

    @Override
    public DeleteFeaturesRequestBuilder newRequestBuilder(ElasticsearchClient client) {
        return new DeleteFeaturesRequestBuilder(client, this);
    }
}
