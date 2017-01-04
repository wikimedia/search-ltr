package org.wikimedia.search.ltr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import org.elasticsearch.action.ActionModule;
import org.elasticsearch.common.component.LifecycleComponent;
import org.elasticsearch.common.inject.AbstractModule;
import org.elasticsearch.common.inject.Module;
import org.elasticsearch.indices.IndicesModule;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.rest.RestModule;
import org.wikimedia.search.ltr.LTRScoringQueryParser;
import org.wikimedia.search.ltr.action.feature.put.PutFeaturesAction;
import org.wikimedia.search.ltr.action.feature.put.TransportPutFeaturesAction;
import org.wikimedia.search.ltr.action.model.put.PutModelsAction;
import org.wikimedia.search.ltr.action.model.put.TransportPutModelsAction;
import org.wikimedia.search.ltr.rest.RestGetFeaturesAction;
import org.wikimedia.search.ltr.rest.RestGetModelsAction;
import org.wikimedia.search.ltr.rest.RestPutFeaturesAction;
import org.wikimedia.search.ltr.rest.RestPutModelsAction;
import org.wikimedia.search.ltr.store.LTRStoreService;

/**
 * Setup the Elasticsearch plugin.
 */
public class LTRPlugin extends Plugin {
    @Override
    public String description() {
        return "Rescoring with Learning to Rank";
    }

    @Override
    public String name() {
        return "wikimedia-ltr";
    }

    /**
     * Register our parsers.
     */
    public void onModule(IndicesModule module) {
        module.registerQueryParser(LTRScoringQueryParser.class);
    }

    /**
     * Register our cluster actions
     */
    public void onModule(ActionModule module) {
        module.registerAction(PutFeaturesAction.INSTANCE, TransportPutFeaturesAction.class);
        module.registerAction(PutModelsAction.INSTANCE, TransportPutModelsAction.class);
    }

    /**
     * Register our REST actions
     */
    public void onModule(RestModule module) {
        module.addRestAction(RestGetFeaturesAction.class);
        module.addRestAction(RestPutFeaturesAction.class);
        module.addRestAction(RestGetModelsAction.class);
        module.addRestAction(RestPutModelsAction.class);
    }

    @Override
    public Collection<Module> nodeModules() {
        List<Module> modules = new ArrayList<>(1);
        modules.add(new LTRStoreModule());
        return Collections.unmodifiableCollection(modules);
    }

    @Override
    public Collection<Class<? extends LifecycleComponent>> nodeServices() {
        return Collections.<Class<? extends LifecycleComponent>>singleton(LTRStoreService.class);
    }

    public static class LTRStoreModule extends AbstractModule {
        @Override
        protected void configure() {
            bind(LTRStoreService.class).asEagerSingleton();
        }
    }
}
