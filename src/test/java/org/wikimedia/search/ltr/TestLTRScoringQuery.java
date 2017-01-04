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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.LuceneTestCase.SuppressSysoutChecks;
import org.elasticsearch.common.settings.Settings;
import org.junit.Test;
import org.wikimedia.search.ltr.feature.Feature;
import org.wikimedia.search.ltr.feature.ValueFeature;
import org.wikimedia.search.ltr.model.LTRScoringModel;
import org.wikimedia.search.ltr.model.ModelException;
import org.wikimedia.search.ltr.model.TestLinearModel;
import org.wikimedia.search.ltr.norm.IdentityNormalizer;
import org.wikimedia.search.ltr.norm.Normalizer;
import org.wikimedia.search.ltr.norm.NormalizerException;

@SuppressSysoutChecks(bugUrl = "not sure why this test prints so much...")
public class TestLTRScoringQuery extends LuceneTestCase {

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

    private static List<Feature> makeFilterFeatures(int[] featureIds) {
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

        // assertEquals(42.0f, score, 0.0001);
        // assertTrue(weight instanceof AssertingWeight);
        // (AssertingIndexSearcher)
        assertTrue(weight instanceof LTRScoringQuery.ModelWeight);
        final LTRScoringQuery.ModelWeight modelWeight = (LTRScoringQuery.ModelWeight) weight;
        return modelWeight;
    }

    @Test
    public void testLTRScoringQueryEquality() throws ModelException {
        final List<Feature> features = makeFeatures(new int[] { 0, 1, 2 });
        final List<Normalizer> norms = new ArrayList<Normalizer>(
            Collections.nCopies(features.size(), IdentityNormalizer.INSTANCE));
        final List<Feature> allFeatures = makeFeatures(new int[] { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9 });
        final Settings modelParams = makeFeatureWeights(features);

        final LTRScoringModel algorithm1 = TestLinearModel.createLinearModel("testModelName", features, norms,
            "testStoreName", allFeatures, modelParams);

        final LTRScoringQuery m0 = new LTRScoringQuery(algorithm1, false, null);

        final Settings externalFeatureInfo = Settings.builder().put("queryIntent", "company").put("user_query", "abc")
            .build();
        final LTRScoringQuery m1 = new LTRScoringQuery(algorithm1, externalFeatureInfo, false, null);

        final Settings externalFeatureInfo2 = Settings.builder().put("user_query", "abc").put("queryIntent", "company")
            .build();
        int totalPoolThreads = 10, numThreadsPerRequest = 10;
        // LTRThreadModule threadManager = new LTRThreadModule(totalPoolThreads,
        // numThreadsPerRequest);
        // final LTRScoringQuery m2 = new LTRScoringQuery(algorithm1,
        // externalFeatureInfo2, false, threadManager);
        final LTRScoringQuery m2 = new LTRScoringQuery(algorithm1, externalFeatureInfo2, false, null);

        // Models with same algorithm and efis, just in different order should
        // be the same
        assertEquals(m1, m2);
        assertEquals(m1.hashCode(), m2.hashCode());

        // Models with same algorithm, but different efi content should not
        // match
        assertFalse(m1.equals(m0));
        assertFalse(m1.hashCode() == m0.hashCode());

        final LTRScoringModel algorithm2 = TestLinearModel.createLinearModel("testModelName2", features, norms,
            "testStoreName", allFeatures, modelParams);
        final LTRScoringQuery m3 = new LTRScoringQuery(algorithm2, false, null);

        assertFalse(m1.equals(m3));
        assertFalse(m1.hashCode() == m3.hashCode());

        final LTRScoringModel algorithm3 = TestLinearModel.createLinearModel("testModelName", features, norms,
            "testStoreName3", allFeatures, modelParams);
        final LTRScoringQuery m4 = new LTRScoringQuery(algorithm3, false, null);

        assertFalse(m1.equals(m4));
        assertFalse(m1.hashCode() == m4.hashCode());
    }

    @Test
    public void testLTRScoringQuery() throws IOException, ModelException {
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
        List<Normalizer> norms = new ArrayList<Normalizer>(
            Collections.nCopies(features.size(), IdentityNormalizer.INSTANCE));
        LTRScoringModel ltrScoringModel = TestLinearModel.createLinearModel("test", features, norms, "test",
            allFeatures, makeFeatureWeights(features));

        LTRScoringQuery.ModelWeight modelWeight = performQuery(hits, searcher, hits.scoreDocs[0].doc,
            new LTRScoringQuery(ltrScoringModel, false, null));
        assertEquals(3, modelWeight.getModelFeatureValuesNormalized().length);

        for (int i = 0; i < 3; i++) {
            assertEquals(i, modelWeight.getModelFeatureValuesNormalized()[i], 0.0001);
        }
        int[] posVals = new int[] { 0, 1, 2 };
        int pos = 0;
        for (LTRScoringQuery.FeatureInfo fInfo : modelWeight.getFeaturesInfo()) {
            if (fInfo == null) {
                continue;
            }
            assertEquals(posVals[pos], fInfo.getValue(), 0.0001);
            assertEquals("f" + posVals[pos], fInfo.getName());
            pos++;
        }

        final int[] mixPositions = new int[] { 8, 2, 4, 9, 0 };
        features = makeFeatures(mixPositions);
        norms = new ArrayList<Normalizer>(Collections.nCopies(features.size(), IdentityNormalizer.INSTANCE));
        ltrScoringModel = TestLinearModel.createLinearModel("test", features, norms, "test", allFeatures,
            makeFeatureWeights(features));

        modelWeight = performQuery(hits, searcher, hits.scoreDocs[0].doc,
            new LTRScoringQuery(ltrScoringModel, false, null));
        assertEquals(mixPositions.length, modelWeight.getModelFeatureWeights().length);

        for (int i = 0; i < mixPositions.length; i++) {
            assertEquals(mixPositions[i], modelWeight.getModelFeatureValuesNormalized()[i], 0.0001);
        }

        final ModelException expectedModelException = new ModelException("no features declared for model test");
        final int[] noPositions = new int[] {};
        features = makeFeatures(noPositions);
        norms = new ArrayList<Normalizer>(Collections.nCopies(features.size(), IdentityNormalizer.INSTANCE));
        try {
            ltrScoringModel = TestLinearModel.createLinearModel("test", features, norms, "test", allFeatures,
                makeFeatureWeights(features));
            fail("unexpectedly got here instead of catching " + expectedModelException);
            modelWeight = performQuery(hits, searcher, hits.scoreDocs[0].doc,
                new LTRScoringQuery(ltrScoringModel, false, null));
            assertEquals(0, modelWeight.getModelFeatureWeights().length);
        } catch (ModelException actualModelException) {
            assertEquals(expectedModelException.toString(), actualModelException.toString());
        }

        // test normalizers
        features = makeFilterFeatures(mixPositions);
        final Normalizer norm = new Normalizer() {

            @Override
            public float normalize(float value) {
                return 42.42f;
            }

            @Override
            public LinkedHashMap<String, Object> paramsToMap() {
                return null;
            }

            @Override
            protected void validate() throws NormalizerException {
            }

        };
        norms = new ArrayList<Normalizer>(Collections.nCopies(features.size(), norm));
        final LTRScoringModel normMeta = TestLinearModel.createLinearModel("test", features, norms, "test", allFeatures,
            makeFeatureWeights(features));

        modelWeight = performQuery(hits, searcher, hits.scoreDocs[0].doc, new LTRScoringQuery(normMeta, false, null));
        normMeta.normalizeFeaturesInPlace(modelWeight.getModelFeatureValuesNormalized());
        assertEquals(mixPositions.length, modelWeight.getModelFeatureWeights().length);
        for (int i = 0; i < mixPositions.length; i++) {
            assertEquals(42.42f, modelWeight.getModelFeatureValuesNormalized()[i], 0.0001);
        }
        r.close();
        dir.close();
    }

}
