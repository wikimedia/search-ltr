package org.wikimedia.search.ltr.action.model.put;

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

public class TransportPutModelsAction extends TransportMasterNodeAction<PutModelsRequest, PutModelsResponse> {
    private final LTRStoreService ltrStoreService;

    @Inject
    public TransportPutModelsAction(Settings settings, TransportService transportService, ClusterService clusterService,
        ThreadPool threadPool, ActionFilters actionFilters, IndexNameExpressionResolver indexNameExpressionResolver,
        LTRStoreService ltrStoreService) {
        super(settings, PutModelsAction.NAME, transportService, clusterService, threadPool, actionFilters,
            indexNameExpressionResolver, PutModelsRequest.class);
        this.ltrStoreService = ltrStoreService;
    }

    @Override
    protected String executor() {
        // no need to use a thread pool, we go async right away
        return ThreadPool.Names.SAME;
    }

    @Override
    protected PutModelsResponse newResponse() {
        return new PutModelsResponse();
    }

    @Override
    protected ClusterBlockException checkBlock(PutModelsRequest request, ClusterState state) {
        // TODO: Is this correct? Do we need a special Block to serialize LTR
        // state update
        // requests?
        return state.blocks().globalBlockedException(ClusterBlockLevel.METADATA_WRITE);
    }

    @Override
    protected void masterOperation(final PutModelsRequest request, final ClusterState state,
        final ActionListener<PutModelsResponse> listener) {
        PutModelsClusterStateUpdateRequest updateRequest = new PutModelsClusterStateUpdateRequest()
            .ackTimeout(request.timeout()).masterNodeTimeout(request.masterNodeTimeout()).models(request.models());

        ltrStoreService.putModels(updateRequest, new ActionListener<ClusterStateUpdateResponse>() {
            @Override
            public void onResponse(ClusterStateUpdateResponse response) {
                listener.onResponse(new PutModelsResponse(response.isAcknowledged()));
            }

            @Override
            public void onFailure(Throwable t) {
                logger.debug("failed to put feature");
                listener.onFailure(t);
            }
        });
    }
}
