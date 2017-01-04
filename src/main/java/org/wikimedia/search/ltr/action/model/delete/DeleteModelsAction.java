package org.wikimedia.search.ltr.action.model.delete;

import org.elasticsearch.action.Action;
import org.elasticsearch.client.ElasticsearchClient;

public class DeleteModelsAction extends Action<DeleteModelsRequest, DeleteModelsResponse, DeleteModelsRequestBuilder> {
    public static final DeleteModelsAction INSTANCE = new DeleteModelsAction();
    public static final String NAME = "cluster:admin/ltr/models/delete";

    private DeleteModelsAction() {
        super(NAME);
    }

    @Override
    public DeleteModelsResponse newResponse() {
        return new DeleteModelsResponse();
    }

    @Override
    public DeleteModelsRequestBuilder newRequestBuilder(ElasticsearchClient client) {
        return new DeleteModelsRequestBuilder(client, this);
    }
}
