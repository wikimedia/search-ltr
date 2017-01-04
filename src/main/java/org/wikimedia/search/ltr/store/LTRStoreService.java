package org.wikimedia.search.ltr.store;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.cluster.AckedClusterStateUpdateTask;
import org.elasticsearch.cluster.ClusterChangedEvent;
import org.elasticsearch.cluster.ClusterService;
import org.elasticsearch.cluster.ClusterState;
import org.elasticsearch.cluster.ClusterStateListener;
import org.elasticsearch.cluster.ack.AckedRequest;
import org.elasticsearch.cluster.ack.ClusterStateUpdateResponse;
import org.elasticsearch.common.component.AbstractLifecycleComponent;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.settings.Settings;
import org.wikimedia.search.ltr.action.feature.delete.DeleteFeaturesClusterStateUpdateRequest;
import org.wikimedia.search.ltr.action.feature.put.PutFeaturesClusterStateUpdateRequest;
import org.wikimedia.search.ltr.action.model.delete.DeleteModelsClusterStateUpdateRequest;
import org.wikimedia.search.ltr.action.model.put.PutModelsClusterStateUpdateRequest;
import org.wikimedia.search.ltr.feature.Feature;
import org.wikimedia.search.ltr.model.LTRScoringModel;
import org.wikimedia.search.ltr.model.ModelException;
import org.wikimedia.search.ltr.norm.IdentityNormalizer;
import org.wikimedia.search.ltr.norm.Normalizer;

public class LTRStoreService extends AbstractLifecycleComponent<LTRStoreService> implements ClusterStateListener {
    static final String CLASS_KEY = "class";
    static final String NAME_KEY = "name";
    static final String FEATURES_KEY = "features";
    static final String NORM_KEY = "norm";
    static final String PARAMS_KEY = "params";
    static final String STORE_KEY = "store";

    private final AtomicReference<ModelStore> modelStore;
    private final ClusterService clusterService;

    private volatile FeaturesState lastFeaturesApplied;
    private volatile ModelsState lastModelsApplied;

    private static final ESLogger log = ESLoggerFactory.getLogger(LTRStoreService.class.getName());

    static {
        ClusterState.registerPrototype(FeaturesState.TYPE, FeaturesState.PROTO);
        ClusterState.registerPrototype(ModelsState.TYPE, ModelsState.PROTO);
    }

    @Inject
    public LTRStoreService(Settings settings, ClusterService clusterService) {
        super(settings);
        modelStore = new AtomicReference<>(new ModelStore());
        this.clusterService = clusterService;
        lastFeaturesApplied = new FeaturesState();
        lastModelsApplied = new ModelsState();
        clusterService.add(this);
    }

    @Override
    protected void doStart() throws ElasticsearchException {
        // Read cluster state now? Or are we guaranteed an opening
        // clusterChanged event?
        // Or does this belong in constructor?
        // loadClusterState(clusterService.state());
    }

    @Override
    protected void doStop() throws ElasticsearchException {
    }

    @Override
    protected void doClose() throws ElasticsearchException {
    }

    @Override
    public void clusterChanged(ClusterChangedEvent event) {
        // master node already applied the state change
        if (!event.localNodeMaster()) {
            loadClusterState(event.state());
        }
    }

    private void loadClusterState(ClusterState state) {
        FeaturesState featuresState = state.custom(FeaturesState.TYPE);
        ModelsState modelsState = state.custom(ModelsState.TYPE);

        // If these are null that means they've never been set in the cluster
        // state, so use an empty version.
        if (featuresState == null) {
            featuresState = new FeaturesState();
        }
        if (modelsState == null) {
            modelsState = new ModelsState();
        }

        if (featuresState.equals(lastFeaturesApplied) && modelsState.equals(lastModelsApplied)) {
            return;
        }

        log.info("[" + clusterService.localNode().getName() + "] Reading new ltr settings");
        modelStore.set(buildModelStore(featuresState, modelsState));
        lastFeaturesApplied = featuresState;
        lastModelsApplied = modelsState;
    }

    abstract class AckedLtrClusterStateUpdateTask extends AckedClusterStateUpdateTask<ClusterStateUpdateResponse> {
        public AckedLtrClusterStateUpdateTask(AckedRequest request,
            ActionListener<ClusterStateUpdateResponse> listener) {
            super(request, listener);
        }

        @Override
        protected ClusterStateUpdateResponse newResponse(boolean acknowledged) {
            return new ClusterStateUpdateResponse(acknowledged);
        }

        protected FeaturesState update(FeaturesState featuresState) {
            return featuresState == null ? new FeaturesState() : featuresState;
        }

        protected ModelsState update(ModelsState modelsState) {
            return modelsState == null ? new ModelsState() : modelsState;
        }

        @Override
        public ClusterState execute(ClusterState currentState) {
            final FeaturesState currentFeaturesState = currentState.custom(FeaturesState.TYPE);
            final ModelsState currentModelsState = currentState.custom(ModelsState.TYPE);
            final FeaturesState newFeaturesState = update(currentFeaturesState);
            final ModelsState newModelsState = update(currentModelsState);
            final ClusterState newState = ClusterState.builder(currentState)
                .putCustom(FeaturesState.TYPE, newFeaturesState).putCustom(ModelsState.TYPE, newModelsState).build();

            // Perhaps a lazy validation ... should extract into
            // something more concrete? But this should throw an
            // exception for any invalid configuration.
            loadClusterState(newState);

            return newState;
        }
    }

    public void deleteFeatures(final DeleteFeaturesClusterStateUpdateRequest request,
        final ActionListener<ClusterStateUpdateResponse> listener) {
        clusterService.submitStateUpdateTask("ltr-delete-features",
            new AckedLtrClusterStateUpdateTask(request, listener) {
                @Override
                protected FeaturesState update(FeaturesState featuresState) {
                    return new FeaturesState.Builder(featuresState).removeAll(request.features()).build();
                }
            });
    }

    public void putFeatures(final PutFeaturesClusterStateUpdateRequest request,
        final ActionListener<ClusterStateUpdateResponse> listener) {
        clusterService.submitStateUpdateTask("ltr-put-features", new AckedLtrClusterStateUpdateTask(request, listener) {
            @Override
            protected FeaturesState update(FeaturesState featuresState) {
                return new FeaturesState.Builder(featuresState).putAll(request.features()).build();
            }
        });
    }

    public void deleteModels(final DeleteModelsClusterStateUpdateRequest request,
        final ActionListener<ClusterStateUpdateResponse> listener) {
        clusterService.submitStateUpdateTask("ltr-delete-models",
            new AckedLtrClusterStateUpdateTask(request, listener) {
                @Override
                protected ModelsState update(ModelsState modelsState) {
                    return new ModelsState.Builder(modelsState).removeAll(request.models()).build();
                }
            });
    }

    public void putModels(final PutModelsClusterStateUpdateRequest request,
        final ActionListener<ClusterStateUpdateResponse> listener) {
        clusterService.submitStateUpdateTask("ltr-put-models", new AckedLtrClusterStateUpdateTask(request, listener) {
            @Override
            protected ModelsState update(ModelsState modelsState) {
                return new ModelsState.Builder(modelsState).putAll(request.models()).build();
            }
        });
    }

    private ModelStore buildModelStore(FeaturesState featuresState, ModelsState modelsState) {
        return buildModelStore(featuresState.features(), modelsState.models());
    }

    /**
     * Only really public so the test case can get access to it
     */
    public ModelStore buildModelStore(Map<String, Map<String, Settings>> features, Map<String, Settings> models) {
        ModelStoreBuilder builder = new ModelStoreBuilder();

        for (final Map.Entry<String, Map<String, Settings>> outerEntry : features.entrySet()) {
            final String featureStore = outerEntry.getKey();
            for (Map.Entry<String, Settings> innerEntry : outerEntry.getValue().entrySet()) {
                builder.addFeature(featureStore, innerEntry.getKey(), innerEntry.getValue());
            }
        }

        for (final Map.Entry<String, Settings> entry : models.entrySet()) {
            builder.addModel(entry.getKey(), entry.getValue());
        }

        return builder.modelStore;
    }

    public LTRScoringModel getModel(String name) {
        return modelStore.get().getModel(name);
    }

    public static class ModelStoreBuilder {
        private HashMap<String, FeatureStore> featureStores = new HashMap<>();
        public ModelStore modelStore = new ModelStore();

        public FeatureStore getFeatureStore(String name) {
            if (name == null) {
                name = FeatureStore.DEFAULT_FEATURE_STORE_NAME;
            }
            if (!featureStores.containsKey(name)) {
                featureStores.put(name, new FeatureStore(name));
            }
            return featureStores.get(name);
        }

        public ModelStoreBuilder addFeature(String featureStore, String featureName, Settings settings) {
            log.info("register feature based on {}", settings);
            final FeatureStore fstore = getFeatureStore(featureStore);
            final Feature feature = fromFeatureSettings(featureName, settings);
            fstore.add(feature);
            return this;
        }

        public ModelStoreBuilder addFeature(String featureStore, Map.Entry<String, Settings> entry) {
            return addFeature(featureStore, entry.getKey(), entry.getValue());
        }

        public ModelStoreBuilder addModel(String modelName, Settings settings) {
            LTRScoringModel model = fromModelSettings(modelName, settings);
            log.info("adding model {}", modelName);
            modelStore.addModel(model);
            return this;
        }

        public ModelStoreBuilder addModel(Map.Entry<String, Settings> entry) {
            return addModel(entry.getKey(), entry.getValue());
        }

        private Feature fromFeatureSettings(String featureName, Settings featureSettings) {
            final String className = featureSettings.get(CLASS_KEY);
            final Settings params = featureSettings.getAsSettings(PARAMS_KEY);

            return Feature.getInstance(className, featureName, params);
        }

        private Feature lookupFeatureFromFeatureSettings(Settings featureSettings, FeatureStore featureStore) {
            final String featureName = featureSettings.get(NAME_KEY);
            if (featureName == null) {
                return null;
            }
            if (featureName.contains(".")) {
                throw new ModelException("Feature names cannot contain periods [" + featureName + "]");
            }
            final Feature feature = featureStore.get(featureName);
            if (feature == null) {
                throw new ModelException("Model refers to unknown feature [" + featureName + "]");
            }
            return feature;
        }

        private Normalizer createNormalizerFromFeatureSettings(Settings featureSettings) {
            final Settings normSettings = featureSettings.getAsSettings(NORM_KEY);
            if (normSettings.getAsStructuredMap().size() == 0) {
                return IdentityNormalizer.INSTANCE;
            }
            return fromNormalizerSettings(normSettings);
        }

        private Normalizer fromNormalizerSettings(Settings normSettings) {
            final String className = normSettings.get(CLASS_KEY);
            final Settings params = normSettings.getAsSettings(PARAMS_KEY);

            return Normalizer.getInstance(className, params);
        }

        private LTRScoringModel fromModelSettings(String modelName, Settings modelSettings) {
            final FeatureStore featureStore = getFeatureStore(modelSettings.get(STORE_KEY));

            final List<Feature> features = new ArrayList<>();
            final List<Normalizer> norms = new ArrayList<>();

            final Map<String, Settings> featureList = modelSettings.getGroups(FEATURES_KEY);
            for (Map.Entry<String, Settings> feature : featureList.entrySet()) {
                final Settings featureSettings = feature.getValue();
                features.add(lookupFeatureFromFeatureSettings(featureSettings, featureStore));
                norms.add(createNormalizerFromFeatureSettings(featureSettings));
            }

            return LTRScoringModel.getInstance(modelSettings.get(CLASS_KEY), modelName, features, norms,
                featureStore.getName(), featureStore.getFeatures(), modelSettings.getAsSettings(PARAMS_KEY));
        }
    }
}
