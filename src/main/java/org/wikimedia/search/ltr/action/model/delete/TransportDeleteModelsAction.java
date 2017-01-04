package org.wikimedia.search.ltr.action.model.delete;

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

public class TransportDeleteModelsAction extends TransportMasterNodeAction<DeleteModelsRequest, DeleteModelsResponse> {
    private final LTRStoreService ltrStoreService;

    @Inject
    public TransportDeleteModelsAction(Settings settings, TransportService transportService, ClusterService clusterService,
        ThreadPool threadPool, ActionFilters actionFilters, IndexNameExpressionResolver indexNameExpressionResolver,
        LTRStoreService ltrStoreService) {
        super(settings, DeleteModelsAction.NAME, transportService, clusterService, threadPool, actionFilters,
            indexNameExpressionResolver, DeleteModelsRequest.class);
        this.ltrStoreService = ltrStoreService;
    }

    @Override
    protected String executor() {
        // no need to use a thread pool, we go async right away
        return ThreadPool.Names.SAME;
    }

    @Override
    protected DeleteModelsResponse newResponse() {
        return new DeleteModelsResponse();
    }

    @Override
    protected ClusterBlockException checkBlock(DeleteModelsRequest request, ClusterState state) {
        // TODO: Is this correct? Do we need a special Block to serialize LTR
        // state update requests?
        return state.blocks().globalBlockedException(ClusterBlockLevel.METADATA_WRITE);
    }

    @Override
    protected void masterOperation(final DeleteModelsRequest request, final ClusterState state,
        final ActionListener<DeleteModelsResponse> listener) {
        DeleteModelsClusterStateUpdateRequest updateRequest = new DeleteModelsClusterStateUpdateRequest()
            .ackTimeout(request.timeout()).masterNodeTimeout(request.masterNodeTimeout()).models(request.models());

        ltrStoreService.deleteModels(updateRequest, new ActionListener<ClusterStateUpdateResponse>() {
            @Override
            public void onResponse(ClusterStateUpdateResponse response) {
                listener.onResponse(new DeleteModelsResponse(response.isAcknowledged()));
            }

            @Override
            public void onFailure(Throwable t) {
                logger.debug("failed to delete feature");
                listener.onFailure(t);
            }
        });
    }
}
