package org.wikimedia.search.ltr.action.model.put;

import org.elasticsearch.cluster.ack.ClusterStateUpdateRequest;
import org.elasticsearch.common.settings.Settings;

public class PutModelsClusterStateUpdateRequest extends ClusterStateUpdateRequest<PutModelsClusterStateUpdateRequest> {
    private Settings models;

    PutModelsClusterStateUpdateRequest() {

    }

    public Settings models() {
        return models;
    }

    public PutModelsClusterStateUpdateRequest models(Settings models) {
        this.models = models;
        return this;
    }
}
