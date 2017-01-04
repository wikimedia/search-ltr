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

import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertFirstHit;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertFourthHit;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertSecondHit;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertThirdHit;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.hasId;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.hasScore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FloatDocValuesField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.index.ReaderUtil;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.Directory;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.settings.Settings;
import org.junit.Before;
import org.junit.Test;
import org.wikimedia.search.ltr.feature.Feature;
import org.wikimedia.search.ltr.feature.ValueFeature;
import org.wikimedia.search.ltr.model.LTRScoringModel;
import org.wikimedia.search.ltr.model.ModelException;
import org.wikimedia.search.ltr.model.TestLinearModel;
import org.wikimedia.search.ltr.norm.IdentityNormalizer;
import org.wikimedia.search.ltr.norm.Normalizer;

import com.google.common.collect.ImmutableMap;

public class TestSelectiveWeightCreation extends TestRerankBase {
    private IndexSearcher getSearcher(IndexReader r) {
        final IndexSearcher searcher = newSearcher(r, false, false);
        return searcher;
    }

    private static List<Feature> makeFeatures(int[] featureIds) {
        final List<Feature> features = new ArrayList<>();
        for (final int i : featureIds) {
            Settings params = Settings.builder().put("value", i).build();
            final Feature f = Feature.getInstance(ValueFeature.class.getCanonicalName(), "f" + i, params);
            f.setIndex(i);
            features.add(f);
        }
        return features;
    }

    private static Settings makeFeatureWeights(List<Feature> features) {
        Settings.Builder builder = Settings.builder();
        for (final Feature feat : features) {
            builder.put("weights." + feat.getName(), 0.1);
        }
        return builder.build();
    }

    private LTRScoringQuery.ModelWeight performQuery(TopDocs hits, IndexSearcher searcher, int docid,
        LTRScoringQuery model) throws IOException, ModelException {
        final List<LeafReaderContext> leafContexts = searcher.getTopReaderContext().leaves();
        final int n = ReaderUtil.subIndex(hits.scoreDocs[0].doc, leafContexts);
        final LeafReaderContext context = leafContexts.get(n);
        final int deBasedDoc = hits.scoreDocs[0].doc - context.docBase;

        final Weight weight = searcher.createNormalizedWeight(model, true);
        final Scorer scorer = weight.scorer(context);

        // rerank using the field final-score
        scorer.iterator().advance(deBasedDoc);
        scorer.score();
        assertTrue(weight instanceof LTRScoringQuery.ModelWeight);
        final LTRScoringQuery.ModelWeight modelWeight = (LTRScoringQuery.ModelWeight) weight;
        return modelWeight;

    }

    @Before
    public void setup() throws Exception {
        setuptest("ltr-mapping.json");

        indexRandom(false, doc("1", "title", "w1 w3", "description", "w1", "popularity", "1"));
        indexRandom(false, doc("2", "title", "w2", "description", "w2", "popularity", "2"));
        indexRandom(false, doc("3", "title", "w3", "description", "w3", "popularity", "3"));
        indexRandom(false, doc("4", "title", "w4 w3 w2", "description", "w4", "popularity", "4"));
        indexRandom(false, doc("5", "title", "w5", "description", "w5", "popularity", "5"));
        refresh();

        loadFeatures("external_features.json");
        loadModels("external_model.json");
        loadModels("external_model_store.json");
    }

    @Test
    public void testScoringQueryWeightCreation() throws IOException, ModelException {
        final Directory dir = newDirectory();
        final RandomIndexWriter w = new RandomIndexWriter(random(), dir);

        Document doc = new Document();
        doc.add(newStringField("id", "0", Field.Store.YES));
        doc.add(newTextField("field", "wizard the the the the the oz", Field.Store.NO));
        doc.add(new FloatDocValuesField("final-score", 1.0f));

        w.addDocument(doc);
        doc = new Document();
        doc.add(newStringField("id", "1", Field.Store.YES));
        // 1 extra token, but wizard and oz are close;
        doc.add(newTextField("field", "wizard oz the the the the the the", Field.Store.NO));
        doc.add(new FloatDocValuesField("final-score", 2.0f));
        w.addDocument(doc);

        final IndexReader r = w.getReader();
        w.close();

        // Do ordinary BooleanQuery:
        final BooleanQuery.Builder bqBuilder = new BooleanQuery.Builder();
        bqBuilder.add(new TermQuery(new Term("field", "wizard")), BooleanClause.Occur.SHOULD);
        bqBuilder.add(new TermQuery(new Term("field", "oz")), BooleanClause.Occur.SHOULD);
        final IndexSearcher searcher = getSearcher(r);
        // first run the standard query
        final TopDocs hits = searcher.search(bqBuilder.build(), 10);
        assertEquals(2, hits.totalHits);
        assertEquals("0", searcher.doc(hits.scoreDocs[0].doc).get("id"));
        assertEquals("1", searcher.doc(hits.scoreDocs[1].doc).get("id"));

        List<Feature> features = makeFeatures(new int[] { 0, 1, 2 });
        final List<Feature> allFeatures = makeFeatures(new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 });
        final List<Normalizer> norms = new ArrayList<>();
        for (int k = 0; k < features.size(); ++k) {
            norms.add(IdentityNormalizer.INSTANCE);
        }

        // when features are NOT requested in the response, only the
        // modelFeature weights should be created
        final LTRScoringModel ltrScoringModel1 = TestLinearModel.createLinearModel("test", features, norms, "test",
            allFeatures, makeFeatureWeights(features));
        LTRScoringQuery.ModelWeight modelWeight = performQuery(hits, searcher, hits.scoreDocs[0].doc,
            new LTRScoringQuery(ltrScoringModel1, false, null)); // features not
        // requested in
        // response
        LTRScoringQuery.FeatureInfo[] featuresInfo = modelWeight.getFeaturesInfo();

        assertEquals(features.size(), modelWeight.getModelFeatureValuesNormalized().length);
        int validFeatures = 0;
        for (int i = 0; i < featuresInfo.length; ++i) {
            if (featuresInfo[i] != null && featuresInfo[i].isUsed()) {
                validFeatures += 1;
            }
        }
        assertEquals(validFeatures, features.size());

        // when features are requested in the response, weights should be
        // created for all features
        final LTRScoringModel ltrScoringModel2 = TestLinearModel.createLinearModel("test", features, norms, "test",
            allFeatures, makeFeatureWeights(features));
        modelWeight = performQuery(hits, searcher, hits.scoreDocs[0].doc,
            new LTRScoringQuery(ltrScoringModel2, true, null));
        // features requested in response
        featuresInfo = modelWeight.getFeaturesInfo();

        assertEquals(features.size(), modelWeight.getModelFeatureValuesNormalized().length);
        assertEquals(allFeatures.size(), modelWeight.getExtractedFeatureWeights().length);

        validFeatures = 0;
        for (int i = 0; i < featuresInfo.length; ++i) {
            if (featuresInfo[i] != null && featuresInfo[i].isUsed()) {
                validFeatures += 1;
            }
        }
        assertEquals(validFeatures, allFeatures.size());

        // Not sure how to recreate these delete by index calls. Are they
        // necessary?
        // assertU(delI("0"))
        // assertU(delI("1"));
        r.close();
        dir.close();
    }

    @Test
    public void testSelectiveWeightsRequestFeaturesFromDifferentStore() throws Exception {
        SearchResponse response = rescoreQuery(
            new LTRScoringQueryBuilder("externalmodel_w_pop").efi(ImmutableMap.<String, String>of("user_query", "w3")));

        assertFirstHit(response, hasId("5"));
        assertSecondHit(response, hasId("4"));
        assertThirdHit(response, hasId("3"));
        assertFourthHit(response, hasId("2"));
        // extract all features in default store
        // assertJQ("/query" + query.toQueryString(),
        // "/response/docs/[0]/fv=='matchedTitle:1.0;titlePhraseMatch:0.40254828'");
        response = rescoreQuery(new LTRScoringQueryBuilder("externalmodel")
            .efi(ImmutableMap.<String, String>of("user_query", "w3", "myPop", "3")));

        // assertFirstHit(response, hasId("1"));
        assertFirstHit(response, hasScore(0.999f));
        // assertJQ("/query" + query.toQueryString(),
        // "/response/docs/[0]/fv=='popularity:3.0;originalScore:1.0'");

        // extract all features from fstore4
        response = rescoreQuery(new LTRScoringQueryBuilder("externalmodelstore")
            .efi(ImmutableMap.<String, String>of("user_query", "w3", "myconf", "0.8", "myPop", "3")));
        // score using fstore2 used by externalmodelstore
        // assertFirstHit(response, hasId("1"));
        assertFirstHit(response, hasScore(0.7992f));
        // extract all features from fstore4
        // assertJQ("/query" + query.toQueryString(),
        // "/response/docs/[0]/fv=='popularity:3.0;originalScore:1.0'");
    }
}
