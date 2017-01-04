package org.wikimedia.search.ltr.action.model.delete;

import java.util.List;
import java.util.Map;

import org.elasticsearch.action.support.master.AcknowledgedRequestBuilder;
import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.common.settings.Settings;

public class DeleteModelsRequestBuilder
    extends AcknowledgedRequestBuilder<DeleteModelsRequest, DeleteModelsResponse, DeleteModelsRequestBuilder> {
    public DeleteModelsRequestBuilder(ElasticsearchClient client, DeleteModelsAction action) {
        super(client, action, new DeleteModelsRequest());
    }

    public DeleteModelsRequestBuilder models(String source) {
        request.models(source);
        return this;
    }

    public DeleteModelsRequestBuilder models(List<Map<String, Object>> map) {
        request.models(map);
        return this;
    }

    public DeleteModelsRequestBuilder models(Settings models) {
        request.models(models);
        return this;
    }

    public DeleteModelsRequestBuilder models(Settings.Builder models) {
        request.models(models);
        return this;
    }
}
