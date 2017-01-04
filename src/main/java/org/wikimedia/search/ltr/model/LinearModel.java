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
import java.util.List;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.Explanation;
import org.elasticsearch.common.settings.Settings;
import org.wikimedia.search.ltr.feature.Feature;
import org.wikimedia.search.ltr.norm.Normalizer;

/**
 * A scoring model that computes scores using a dot product. Example models are
 * RankSVM and Pranking.
 * <p>
 * Example configuration:
 *
 * <pre>
 * {
   "class" : "org.wikimedia.search.ltr.model.LinearModel",
   "name" : "myModelName",
   "features" : [
       { "name" : "userTextTitleMatch" },
       { "name" : "originalScore" },
       { "name" : "isBook" }
   ],
   "params" : {
       "weights" : {
           "userTextTitleMatch" : 1.0,
           "originalScore" : 0.5,
           "isBook" : 0.1
       }
   }
}
 * </pre>
 * <p>
 * Background reading:
 * <ul>
 * <li>
 * <a href="http://www.cs.cornell.edu/people/tj/publications/joachims_02c.pdf">
 * Thorsten Joachims. Optimizing Search Engines Using Clickthrough Data.
 * Proceedings of the ACM Conference on Knowledge Discovery and Data Mining
 * (KDD), ACM, 2002.</a>
 * </ul>
 * <ul>
 * <li><a href="https://papers.nips.cc/paper/2023-pranking-with-ranking.pdf">
 * Koby Crammer and Yoram Singer. Pranking with Ranking. Advances in Neural
 * Information Processing Systems (NIPS), 2001.</a>
 * </ul>
 */
public class LinearModel extends LTRScoringModel {

    static final String WEIGHTS_KEY = "weights";

    protected Float[] featureToWeight;

    private void setWeights(Settings weights) {
        for (int ii = 0; ii < features.size(); ++ii) {
            final String key = features.get(ii).getName();
            final Double val = weights.getAsDouble(key, null);
            featureToWeight[ii] = (val == null ? null : new Float(val.floatValue()));
        }
    }

    public LinearModel(String name, List<Feature> features, List<Normalizer> norms, String featureStoreName,
        List<Feature> allFeatures, Settings params) {
        super(name, features, norms, featureStoreName, allFeatures, params);
        featureToWeight = new Float[features.size()];
        setWeights(params.getAsSettings(WEIGHTS_KEY));
    }

    @Override
    public void validate() throws ModelException {
        super.validate();

        if (features.size() == 0) {
            throw new ModelException("Model " + name + " doesn't contain any features");
        }
        final ArrayList<String> missingWeightFeatureNames = new ArrayList<String>();
        for (int i = 0; i < features.size(); ++i) {
            if (featureToWeight[i] == null) {
                missingWeightFeatureNames.add(features.get(i).getName());
            }
        }
        if (missingWeightFeatureNames.size() == features.size()) {
            throw new ModelException("Model " + name + " doesn't contain any weights");
        }
        if (!missingWeightFeatureNames.isEmpty()) {
            throw new ModelException("Model " + name + " lacks weight(s) for " + missingWeightFeatureNames);
        }
    }

    @Override
    public float score(float[] modelFeatureValuesNormalized) {
        float score = 0;
        for (int i = 0; i < modelFeatureValuesNormalized.length; ++i) {
            score += modelFeatureValuesNormalized[i] * featureToWeight[i];
        }
        return score;
    }

    @Override
    public Explanation explain(LeafReaderContext context, int doc, float finalScore,
        List<Explanation> featureExplanations) {
        final List<Explanation> details = new ArrayList<>();
        int index = 0;

        for (final Explanation featureExplain : featureExplanations) {
            final List<Explanation> featureDetails = new ArrayList<>();
            featureDetails.add(Explanation.match(featureToWeight[index], "weight on feature"));
            featureDetails.add(featureExplain);

            details
                .add(Explanation.match(featureExplain.getValue() * featureToWeight[index], "prod of:", featureDetails));
            index++;
        }

        return Explanation.match(finalScore, toString() + " model applied to features, sum of:", details);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(getClass().getSimpleName());
        sb.append("(name=").append(getName());
        sb.append(",featureWeights=[");
        for (int ii = 0; ii < features.size(); ++ii) {
            if (ii > 0)
                sb.append(',');
            final String key = features.get(ii).getName();
            sb.append(key).append('=').append(featureToWeight[ii]);
        }
        sb.append("])");
        return sb.toString();
    }

}
