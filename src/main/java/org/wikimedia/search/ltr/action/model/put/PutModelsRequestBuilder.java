package org.wikimedia.search.ltr.action.model.put;

import java.util.Map;

import org.elasticsearch.action.support.master.AcknowledgedRequestBuilder;
import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.common.settings.Settings;

public class PutModelsRequestBuilder
    extends AcknowledgedRequestBuilder<PutModelsRequest, PutModelsResponse, PutModelsRequestBuilder> {
    public PutModelsRequestBuilder(ElasticsearchClient client, PutModelsAction action) {
        super(client, action, new PutModelsRequest());
    }

    public PutModelsRequestBuilder models(String source) {
        request.models(source);
        return this;
    }

    public PutModelsRequestBuilder models(Settings models) {
        request.models(models);
        return this;
    }

    public PutModelsRequestBuilder models(Settings.Builder models) {
        request.models(models);
        return this;
    }

    public PutModelsRequestBuilder models(Map<String, Map<String, Object>> models) {
        request.models(models);
        return this;
    }
}
