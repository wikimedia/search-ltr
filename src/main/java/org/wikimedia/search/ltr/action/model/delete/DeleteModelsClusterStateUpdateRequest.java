package org.wikimedia.search.ltr.action.model.delete;

import org.elasticsearch.cluster.ack.ClusterStateUpdateRequest;
import org.elasticsearch.common.settings.Settings;

public class DeleteModelsClusterStateUpdateRequest extends ClusterStateUpdateRequest<DeleteModelsClusterStateUpdateRequest> {
    private Settings models;

    DeleteModelsClusterStateUpdateRequest() {

    }

    public Settings models() {
        return models;
    }

    public DeleteModelsClusterStateUpdateRequest models(Settings models) {
        this.models = models;
        return this;
    }
}
