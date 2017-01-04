/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wikimedia.search.ltr;

import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertAcked;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchType;
import org.elasticsearch.client.Requests;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.query.MatchAllQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.fieldvaluefactor.FieldValueFactorFunctionBuilder;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.script.expression.ExpressionPlugin;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.rescore.RescoreBuilder;
import org.elasticsearch.test.ESIntegTestCase;
import org.elasticsearch.test.ESIntegTestCase.ClusterScope;
import org.wikimedia.search.ltr.action.feature.put.PutFeaturesAction;
import org.wikimedia.search.ltr.action.feature.put.PutFeaturesResponse;
import org.wikimedia.search.ltr.action.model.put.PutModelsAction;
import org.wikimedia.search.ltr.action.model.put.PutModelsResponse;
import org.wikimedia.search.ltr.feature.Feature;
import org.wikimedia.search.ltr.feature.FeatureException;
import org.wikimedia.search.ltr.feature.ValueFeature;
import org.wikimedia.search.ltr.model.LTRScoringModel;
import org.wikimedia.search.ltr.model.LinearModel;
import org.wikimedia.search.ltr.store.LTRStoreService;
import org.wikimedia.search.ltr.store.LTRStoreService.ModelStoreBuilder;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;

/**
 * This needs to use TEST scope because we apply cluster settings. It will make
 * the tests slow, but not sure a way around it yet.
 */
@ClusterScope(scope = ESIntegTestCase.Scope.TEST, transportClientRatio = 0.0)
public class TestRerankBase extends AbstractPluginIntegrationTest {
    private static ESLogger log = ESLoggerFactory.getLogger(TestRerankBase.class.getCanonicalName());

    @Override
    protected Collection<Class<? extends Plugin>> nodePlugins() {
        return ImmutableList.<Class<? extends Plugin>>builder().addAll(super.nodePlugins()).add(ExpressionPlugin.class)
            .build();
    }

    public static LTRStoreService getLTRStoreService() {
        return new LTRStoreService(Settings.EMPTY, null);
    }

    protected static LTRStoreService.ModelStoreBuilder modelStoreBuilder() {
        return new LTRStoreService.ModelStoreBuilder();
    }

    protected void setuptest(String mappingFile) throws Exception {
        String mapping = Resources.toString(Resources.getResource(mappingFile), Charsets.UTF_8);
        assertAcked(prepareCreate("test").addMapping("test", mapping));
        ensureGreen();
    }

    /**
     * Adjusting query scores with popularity gives us a consistent sorting
     * order.
     *
     * @param builder
     * @return
     */
    protected SearchResponse queryWithPopularity(QueryBuilder rescore) throws Exception {
        QueryBuilder query = new FunctionScoreQueryBuilder(new FieldValueFactorFunctionBuilder("popularity"));
        return rescoreQuery(1f, query, rescore);
    }

    protected SearchResponse rescoreQuery(QueryBuilder rescore) throws Exception {
        return rescoreQuery(0f, null, rescore);
    }

    protected SearchResponse rescoreQuery(QueryBuilder query, QueryBuilder rescore) throws Exception {
        return rescoreQuery(0f, query, rescore);
    }

    protected SearchSourceBuilder rescoreQueryBuilder(QueryBuilder query, QueryBuilder rescore) throws Exception {
        return rescoreQueryBuilder(0f, query, rescore);
    }

    protected SearchSourceBuilder rescoreQueryBuilder(float queryWeight, QueryBuilder query, QueryBuilder rescore)
        throws Exception {
        SearchSourceBuilder builder = SearchSourceBuilder.searchSource();
        if (rescore != null) {
            builder.addRescorer(new RescoreBuilder()
                .rescorer(RescoreBuilder.queryRescorer(rescore).setQueryWeight(queryWeight)).windowSize(1024));
        }
        if (query == null) {
            builder.query(new MatchAllQueryBuilder());
        } else {
            builder.query(query);
        }
        return builder;
    }

    // TODO: remove withExplain, let callers handle it with rescoreQueryBuilder
    // + doSearch directly
    protected SearchResponse rescoreQuery(float queryWeight, QueryBuilder query, QueryBuilder rescore)
        throws Exception {
        SearchSourceBuilder builder = rescoreQueryBuilder(queryWeight, query, rescore);
        return doSearch(builder);
    }

    protected SearchResponse doSearch(SearchSourceBuilder builder) throws Exception {
        return client()
            .search(Requests.searchRequest("test").searchType(SearchType.DFS_QUERY_THEN_FETCH).source(builder)).get();
    }

    protected List<Feature> getFeatures(List<String> names) throws FeatureException {
        final List<Feature> features = new ArrayList<>();
        int pos = 0;
        for (final String name : names) {
            final Settings params = Settings.builder().put("value", 10).build();
            final Feature f = Feature.getInstance(ValueFeature.class.getCanonicalName(), name, params);
            f.setIndex(pos);
            features.add(f);
            ++pos;
        }
        return features;
    }

    protected List<Feature> getFeatures(String[] names) throws FeatureException {
        return getFeatures(Arrays.asList(names));
    }

    protected IndexRequestBuilder doc(String id, Object... source) {
        return client().prepareIndex("test", "test", id).setSource(source);
    }

    protected Map<String, Object> createFeatureMap(String type, Map<String, Object> params) {
        if (params == null) {
            params = ImmutableMap.<String, Object>of();
        }
        return ImmutableMap.<String, Object>of("store", "test", "class", type, "params", params);
    }

    protected void loadFeature(String name, String type, Map<String, Object> params) throws Exception {
        Map<String, Map<String, Object>> feature = ImmutableMap.<String, Map<String, Object>>of(name,
            createFeatureMap(type, params));
        Map<String, Map<String, Map<String, Object>>> features = ImmutableMap
            .<String, Map<String, Map<String, Object>>>of("test", feature);
        loadFeatures(features);
    }

    // For generating queries used with ESQueryFeature
    protected Map<String, Object> toXContentAsMap(ToXContent query) throws Exception {
        XContentBuilder builder = XContentFactory.jsonBuilder();
        query.toXContent(builder, ToXContent.EMPTY_PARAMS);
        return JsonXContent.jsonXContent.createParser(builder.string()).map();
    }

    protected void loadFeatures(Map<String, Map<String, Map<String, Object>>> features) throws Exception {
        PutFeaturesResponse response = PutFeaturesAction.INSTANCE.newRequestBuilder(client()).features(features)
            .execute().get();
        assertTrue(response.isAcknowledged());
    }

    protected void loadFeatures(String fileName) throws Exception {
        String source = Resources.toString(Resources.getResource("featureExamples/" + fileName), Charsets.UTF_8);
        PutFeaturesResponse response = PutFeaturesAction.INSTANCE.newRequestBuilder(client()).features(source).execute()
            .get();
        assertTrue(response.isAcknowledged());
    }

    protected Map<String, Object> createModelMap(String type, List<Map<String, Object>> features,
        Map<String, Object> params) {
        return ImmutableMap.<String, Object>of("store", "test", "class", type, "features", features, "params", params);
    }

    protected void loadModel(String name, String type, List<Map<String, Object>> features, Map<String, Object> params)
        throws Exception {
        loadModel(name, createModelMap(type, features, params));
    }

    protected void loadModel(String modelName, Map<String, Object> model) throws Exception {
        loadModels(ImmutableMap.<String, Map<String, Object>>of(modelName, model));
    }

    protected void loadModels(Map<String, Map<String, Object>> models) throws Exception {
        PutModelsResponse response = PutModelsAction.INSTANCE.newRequestBuilder(client()).models(models).execute()
            .get();
        assertTrue(response.isAcknowledged());
    }

    protected void loadModels(String fileName) throws Exception {
        String source = Resources.toString(Resources.getResource("modelExamples/" + fileName), Charsets.UTF_8);
        PutModelsResponse response = PutModelsAction.INSTANCE.newRequestBuilder(client()).models(source).execute()
            .get();
        assertTrue(response.isAcknowledged());
    }

    protected void loadModelAndFeatures(String name, int allFeatureCount, int modelFeatureCount) throws Exception {
        Map<String, Map<String, Object>> featureStore = new HashMap<>();

        Settings.Builder builder = Settings.builder();
        builder.put("class", LinearModel.class.getCanonicalName());
        for (int i = 0; i < allFeatureCount; i++) {
            final String featureName = "c" + i;
            if (i < modelFeatureCount) {
                builder.put("features." + i + ".name", featureName);
                builder.put("params.weights." + featureName, 1.0);
            }
            featureStore.put(featureName,
                createFeatureMap(ValueFeature.class.getCanonicalName(), ImmutableMap.<String, Object>of("value", i)));

        }
        loadFeatures(ImmutableMap.<String, Map<String, Map<String, Object>>>of("_DEFAULT_", featureStore));
        loadModel(name, builder.build().getAsStructuredMap());
    }

    protected LTRScoringModel createModelFromFiles(String modelFile, String featureFile) throws Exception {
        String modelsString = Resources.toString(getClass().getResource("/modelExamples/" + modelFile), Charsets.UTF_8);
        Settings models = Settings.builder().loadFromSource(modelsString).normalizePrefix("models.").build();

        String featuresString = Resources.toString(getClass().getResource("/featureExamples/" + featureFile),
            Charsets.UTF_8);
        Settings features = Settings.builder().loadFromSource(featuresString).normalizePrefix("features.").build();

        ModelStoreBuilder builder = new ModelStoreBuilder();
        for (Map.Entry<String, Settings> outerEntry : features.getGroups("features").entrySet()) {
            Settings fstore = Settings.builder().put(outerEntry.getValue()).normalizePrefix("features.").build();
            for (Map.Entry<String, Settings> entry : fstore.getGroups("features").entrySet()) {
                builder.addFeature(outerEntry.getKey(), entry);
            }
        }
        Map<String, Settings> modelMap = models.getGroups("models");
        assert modelMap.size() == 1;
        String modelName = modelMap.keySet().iterator().next();
        builder.addModel(modelName, modelMap.get(modelName));

        return builder.modelStore.getModel(modelName);
    }

    protected Settings mapToSettings(Map<String, Object> map) throws Exception {
        String json = JsonXContent.contentBuilder().map(map).string();
        return Settings.builder().loadFromSource(json).build();
    }

    protected Throwable getRootCause(Throwable t) {
        Throwable result = t;
        for (Throwable cause = t; cause != null; cause = cause.getCause()) {
            result = cause;
        }
        return result;
    }
    /*
     * protected static void bulkIndex() throws Exception {
     * assertU(adoc("title", "bloomberg different bla", "description",
     * "bloomberg", "id", "6", "popularity", "1")); assertU(adoc("title",
     * "bloomberg bloomberg ", "description", "bloomberg", "id", "7",
     * "popularity", "2")); assertU(adoc("title",
     * "bloomberg bloomberg bloomberg", "description", "bloomberg", "id", "8",
     * "popularity", "3")); assertU(adoc("title",
     * "bloomberg bloomberg bloomberg bloomberg", "description", "bloomberg",
     * "id", "9", "popularity", "5")); assertU(commit()); }
     */
}
