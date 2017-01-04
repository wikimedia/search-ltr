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
package org.wikimedia.search.ltr.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.elasticsearch.common.settings.Settings;
import org.junit.Test;
import org.wikimedia.search.ltr.TestRerankBase;
import org.wikimedia.search.ltr.feature.Feature;
import org.wikimedia.search.ltr.feature.ValueFeature;
import org.wikimedia.search.ltr.norm.IdentityNormalizer;
import org.wikimedia.search.ltr.norm.Normalizer;
import org.wikimedia.search.ltr.store.LTRStoreService;
import org.wikimedia.search.ltr.store.ModelStore;

// TODO: This starts up an ES cluster but we don't actually need that. Break up
// TestRerankBase into a plain base class, and one that boots a cluster?
public class TestLinearModel extends TestRerankBase {

    private ModelStore store = new ModelStore();

    public static LTRScoringModel createLinearModel(String name, List<Feature> features, List<Normalizer> norms,
        String featureStoreName, List<Feature> allFeatures, Settings params) throws ModelException {
        final LTRScoringModel model = LTRScoringModel.getInstance(LinearModel.class.getCanonicalName(), name, features,
            norms, featureStoreName, allFeatures, params);
        return model;
    }

    @Test
    public void getInstanceTest() {
        ModelStore store = (new LTRStoreService.ModelStoreBuilder())
            .addFeature("_DEFAULT_", "constant1",
                Settings.builder().put("class", ValueFeature.class).put("params.value", 42).build())
            .addFeature("_DEFAULT_", "constant5",
                Settings.builder().put("class", ValueFeature.class).put("params.value", 42).build())
            .addModel("test1",
                Settings.builder().put("class", LinearModel.class).put("features.0.name", "constant1")
                    .put("features.1.name", "constant5").put("params.weights.constant1", 1d)
                    .put("params.weights.constant5", 1d).build()).modelStore;

        final LTRScoringModel model = store.getModel("test1");
        assertEquals(model.toString(), "LinearModel(name=test1,featureWeights=[constant1=1.0,constant5=1.0])");
    }

    @Test
    public void nullFeatureWeightsTest() {
        final ModelException expectedException = new ModelException("Model test2 doesn't contain any weights");
        try {
            final List<Feature> features = getFeatures(new String[] { "constant1", "constant5" });
            final List<Normalizer> norms = new ArrayList<Normalizer>(
                Collections.nCopies(features.size(), IdentityNormalizer.INSTANCE));
            createLinearModel("test2", features, norms, "test", features, Settings.EMPTY);
            fail("unexpectedly got here instead of catching " + expectedException);
        } catch (ModelException actualException) {
            assertEquals(expectedException.toString(), actualException.toString());
        }
    }

    @Test
    public void existingNameTest() {
        final ModelException expectedException = new ModelException(
            "model 'test3' already exists. Please use a different name");
        try {
            final List<Feature> features = getFeatures(new String[] { "constant1", "constant5" });
            final List<Normalizer> norms = new ArrayList<Normalizer>(
                Collections.nCopies(features.size(), IdentityNormalizer.INSTANCE));
            Settings params = Settings.builder().put("weights.constant1", 1d).put("weights.constant5", 1d).build();

            final LTRScoringModel ltrScoringModel = createLinearModel("test3", features, norms, "test", features,
                params);
            store.addModel(ltrScoringModel);
            final LTRScoringModel m = store.getModel("test3");
            assertEquals(ltrScoringModel, m);
            store.addModel(ltrScoringModel);
            fail("unexpectedly got here instead of catching " + expectedException);
        } catch (ModelException actualException) {
            assertEquals(expectedException.toString(), actualException.toString());
        }
    }

    @Test
    public void duplicateFeatureTest() {
        final ModelException expectedException = new ModelException("duplicated feature constant1 in model test4");
        try {
            final List<Feature> features = getFeatures(new String[] { "constant1", "constant1" });
            final List<Normalizer> norms = new ArrayList<Normalizer>(
                Collections.nCopies(features.size(), IdentityNormalizer.INSTANCE));

            Settings params = Settings.builder().put("weights.constant1", 1d).put("weights.constant5", 1d).build();
            final LTRScoringModel ltrScoringModel = createLinearModel("test4", features, norms, "test", features,
                params);
            store.addModel(ltrScoringModel);
            fail("unexpectedly got here instead of catching " + expectedException);
        } catch (ModelException actualException) {
            assertEquals(expectedException.toString(), actualException.toString());
        }

    }

    @Test
    public void missingFeatureWeightTest() {
        final ModelException expectedException = new ModelException("Model test5 lacks weight(s) for [constant5]");
        try {
            final List<Feature> features = getFeatures(new String[] { "constant1", "constant5" });
            final List<Normalizer> norms = new ArrayList<Normalizer>(
                Collections.nCopies(features.size(), IdentityNormalizer.INSTANCE));

            Settings params = Settings.builder().put("weights.constant1", 1d).put("weights.constant5missing", 1d)
                .build();
            createLinearModel("test5", features, norms, "test", features, params);
            fail("unexpectedly got here instead of catching " + expectedException);
        } catch (ModelException actualException) {
            assertEquals(expectedException.toString(), actualException.toString());
        }
    }

    @Test
    public void emptyFeaturesTest() {
        final ModelException expectedException = new ModelException("no features declared for model test6");
        try {
            final List<Feature> features = getFeatures(new String[] {});
            final List<Normalizer> norms = new ArrayList<Normalizer>(
                Collections.nCopies(features.size(), IdentityNormalizer.INSTANCE));

            Settings params = Settings.builder().put("weights.constant1", 1d).put("weights.constant5missing", 1d)
                .build();
            createLinearModel("test6", features, norms, "test", features, params);
            fail("unexpectedly got here instead of catching " + expectedException);
        } catch (ModelException actualException) {
            assertEquals(expectedException.toString(), actualException.toString());
        }
    }

}
