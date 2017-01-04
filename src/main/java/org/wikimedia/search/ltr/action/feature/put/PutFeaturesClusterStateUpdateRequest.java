package org.wikimedia.search.ltr.action.feature.put;

import org.elasticsearch.cluster.ack.ClusterStateUpdateRequest;
import org.elasticsearch.common.settings.Settings;

public class PutFeaturesClusterStateUpdateRequest
    extends ClusterStateUpdateRequest<PutFeaturesClusterStateUpdateRequest> {
    private Settings features;

    PutFeaturesClusterStateUpdateRequest() {

    }

    public Settings features() {
        return features;
    }

    public PutFeaturesClusterStateUpdateRequest features(Settings features) {
        this.features = features;
        return this;
    }
}
