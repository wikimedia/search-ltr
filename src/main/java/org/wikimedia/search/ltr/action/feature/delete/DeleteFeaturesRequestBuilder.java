package org.wikimedia.search.ltr.action.feature.delete;

import java.util.List;
import java.util.Map;

import org.elasticsearch.action.support.master.AcknowledgedRequestBuilder;
import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.common.settings.Settings;

public class DeleteFeaturesRequestBuilder
    extends AcknowledgedRequestBuilder<DeleteFeaturesRequest, DeleteFeaturesResponse, DeleteFeaturesRequestBuilder> {
    public DeleteFeaturesRequestBuilder(ElasticsearchClient client, DeleteFeaturesAction action) {
        super(client, action, new DeleteFeaturesRequest());
    }

    public DeleteFeaturesRequestBuilder features(String source) {
        request.features(source);
        return this;
    }

    public DeleteFeaturesRequestBuilder features(Map<String, List<String>> features) {
        request.features(features);
        return this;
    }

    public DeleteFeaturesRequestBuilder features(Settings features) {
        request.features(features);
        return this;
    }

    public DeleteFeaturesRequestBuilder features(Settings.Builder features) {
        request.features(features);
        return this;
    }
}
