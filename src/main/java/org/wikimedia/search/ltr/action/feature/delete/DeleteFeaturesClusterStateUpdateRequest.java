package org.wikimedia.search.ltr.action.feature.delete;

import org.elasticsearch.cluster.ack.ClusterStateUpdateRequest;
import org.elasticsearch.common.settings.Settings;

public class DeleteFeaturesClusterStateUpdateRequest
    extends ClusterStateUpdateRequest<DeleteFeaturesClusterStateUpdateRequest> {
    private Settings features;

    DeleteFeaturesClusterStateUpdateRequest() {

    }

    public Settings features() {
        return features;
    }

    public DeleteFeaturesClusterStateUpdateRequest features(Settings features) {
        this.features = features;
        return this;
    }
}
