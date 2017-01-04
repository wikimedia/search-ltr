package org.wikimedia.search.ltr.action.feature.delete;

import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.support.ActionFilters;
import org.elasticsearch.action.support.master.TransportMasterNodeAction;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.ack.ClusterStateUpdateResponse;
import org.elasticsearch.cluster.block.ClusterBlockException;
import org.elasticsearch.cluster.block.ClusterBlockLevel;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.threadpool.ThreadPool;
import org.elasticsearch.transport.TransportService;
import org.wikimedia.search.ltr.store.LTRStoreService;

public class TransportDeleteFeaturesAction extends TransportMasterNodeAction<DeleteFeaturesRequest, DeleteFeaturesResponse> {
    private final LTRStoreService ltrStoreService;

    @Inject
    public TransportDeleteFeaturesAction(Settings settings, TransportService transportService,
        ClusterService clusterService, ThreadPool threadPool, ActionFilters actionFilters,
        IndexNameExpressionResolver indexNameExpressionResolver, LTRStoreService ltrStoreService) {
        super(settings, DeleteFeaturesAction.NAME, transportService, clusterService, threadPool, actionFilters,
            indexNameExpressionResolver, DeleteFeaturesRequest.class);
        this.ltrStoreService = ltrStoreService;
    }

    @Override
    protected String executor() {
        // no need to use a thread pool, we go async right away
        return ThreadPool.Names.SAME;
    }

    @Override
    protected DeleteFeaturesResponse newResponse() {
        return new DeleteFeaturesResponse();
    }

    @Override
    protected ClusterBlockException checkBlock(DeleteFeaturesRequest request, ClusterState state) {
        // TODO: Is this correct? Do we need a special Block to serialize LTR
        // state update requests?
        return state.blocks().globalBlockedException(ClusterBlockLevel.METADATA_WRITE);
    }

    @Override
    protected void masterOperation(final DeleteFeaturesRequest request, final ClusterState state,
        final ActionListener<DeleteFeaturesResponse> listener) {
        DeleteFeaturesClusterStateUpdateRequest updateRequest = new DeleteFeaturesClusterStateUpdateRequest()
            .ackTimeout(request.timeout()).masterNodeTimeout(request.masterNodeTimeout()).features(request.features());

        ltrStoreService.deleteFeatures(updateRequest, new ActionListener<ClusterStateUpdateResponse>() {
            @Override
            public void onResponse(ClusterStateUpdateResponse response) {
                listener.onResponse(new DeleteFeaturesResponse(response.isAcknowledged()));
            }

            @Override
            public void onFailure(Throwable t) {
                logger.debug("failed to delete feature");
                listener.onFailure(t);
            }
        });
    }
}
