package org.wikimedia.search.ltr.action.feature.put;

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

public class TransportPutFeaturesAction extends TransportMasterNodeAction<PutFeaturesRequest, PutFeaturesResponse> {
    private final LTRStoreService ltrStoreService;

    @Inject
    public TransportPutFeaturesAction(Settings settings, TransportService transportService,
        ClusterService clusterService, ThreadPool threadPool, ActionFilters actionFilters,
        IndexNameExpressionResolver indexNameExpressionResolver, LTRStoreService ltrStoreService) {
        super(settings, PutFeaturesAction.NAME, transportService, clusterService, threadPool, actionFilters,
            indexNameExpressionResolver, PutFeaturesRequest.class);
        this.ltrStoreService = ltrStoreService;
    }

    @Override
    protected String executor() {
        // no need to use a thread pool, we go async right away
        return ThreadPool.Names.SAME;
    }

    @Override
    protected PutFeaturesResponse newResponse() {
        return new PutFeaturesResponse();
    }

    @Override
    protected ClusterBlockException checkBlock(PutFeaturesRequest request, ClusterState state) {
        // TODO: Is this correct? Do we need a special Block to serialize LTR
        // state update requests?
        return state.blocks().globalBlockedException(ClusterBlockLevel.METADATA_WRITE);
    }

    @Override
    protected void masterOperation(final PutFeaturesRequest request, final ClusterState state,
        final ActionListener<PutFeaturesResponse> listener) {
        PutFeaturesClusterStateUpdateRequest updateRequest = new PutFeaturesClusterStateUpdateRequest()
            .ackTimeout(request.timeout()).masterNodeTimeout(request.masterNodeTimeout()).features(request.features());

        ltrStoreService.putFeatures(updateRequest, new ActionListener<ClusterStateUpdateResponse>() {
            @Override
            public void onResponse(ClusterStateUpdateResponse response) {
                listener.onResponse(new PutFeaturesResponse(response.isAcknowledged()));
            }

            @Override
            public void onFailure(Throwable t) {
                logger.debug("failed to put feature");
                listener.onFailure(t);
            }
        });
    }
}
