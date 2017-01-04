package org.wikimedia.search.ltr.action.model.put;

import org.elasticsearch.action.Action;
import org.elasticsearch.client.ElasticsearchClient;

public class PutModelsAction extends Action<PutModelsRequest, PutModelsResponse, PutModelsRequestBuilder> {
    public static final PutModelsAction INSTANCE = new PutModelsAction();
    public static final String NAME = "cluster:admin/ltr/models/put";

    private PutModelsAction() {
        super(NAME);
    }

    @Override
    public PutModelsResponse newResponse() {
        return new PutModelsResponse();
    }

    @Override
    public PutModelsRequestBuilder newRequestBuilder(ElasticsearchClient client) {
        return new PutModelsRequestBuilder(client, this);
    }
}
