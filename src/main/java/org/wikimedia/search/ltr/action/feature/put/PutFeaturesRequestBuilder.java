package org.wikimedia.search.ltr.action.feature.put;

import java.util.Map;

import org.elasticsearch.action.support.master.AcknowledgedRequestBuilder;
import org.elasticsearch.client.ElasticsearchClient;
import org.elasticsearch.common.settings.Settings;

public class PutFeaturesRequestBuilder
    extends AcknowledgedRequestBuilder<PutFeaturesRequest, PutFeaturesResponse, PutFeaturesRequestBuilder> {
    public PutFeaturesRequestBuilder(ElasticsearchClient client, PutFeaturesAction action) {
        super(client, action, new PutFeaturesRequest());
    }

    public PutFeaturesRequestBuilder features(String source) {
        request.features(source);
        return this;
    }

    public PutFeaturesRequestBuilder features(Map<String, Map<String, Map<String, Object>>> map) {
        request.features(map);
        return this;
    }

    public PutFeaturesRequestBuilder features(Settings features) {
        request.features(features);
        return this;
    }

    public PutFeaturesRequestBuilder features(Settings.Builder features) {
        request.features(features);
        return this;
    }
}
