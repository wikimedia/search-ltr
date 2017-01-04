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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.Explanation;
import org.elasticsearch.common.settings.Settings;
import org.wikimedia.search.ltr.feature.Feature;
import org.wikimedia.search.ltr.norm.Normalizer;

/**
 * A scoring model that computes scores based on the summation of multiple
 * weighted trees. Example models are LambdaMART and Gradient Boosted Regression
 * Trees (GBRT) .
 * <p>
 * Example configuration:
 *
 * <pre>
 * {
   "class" : "org.wikimedia.search.ltr.model.MultipleAdditiveTreesModel",
   "name" : "multipleadditivetreesmodel",
   "features":[
       { "name" : "userTextTitleMatch"},
       { "name" : "originalScore"}
   ],
   "params" : {
       "trees" : [
           {
               "weight" : 1,
               "root": {
                   "feature" : "userTextTitleMatch",
                   "threshold" : 0.5,
                   "left" : {
                       "value" : -100
                   },
                   "right" : {
                       "feature" : "originalScore",
                       "threshold" : 10.0,
                       "left" : {
                           "value" : 50
                       },
                       "right" : {
                           "value" : 75
                       }
                   }
               }
           },
           {
               "weight" : 2,
               "root" : {
                   "value" : -10
               }
           }
       ]
   }
}
 * </pre>
 * <p>
 * Training libraries:
 * <ul>
 * <li><a href="http://sourceforge.net/p/lemur/wiki/RankLib/">RankLib</a>
 * </ul>
 * <p>
 * Background reading:
 * <ul>
 * <li><a href="http://research.microsoft.com/pubs/132652/MSR-TR-2010-82.pdf">
 * Christopher J.C. Burges. From RankNet to LambdaRank to LambdaMART: An
 * Overview. Microsoft Research Technical Report MSR-TR-2010-82.</a>
 * </ul>
 * <ul>
 * <li><a href=
 * "https://papers.nips.cc/paper/3305-a-general-boosting-method-and-its-application-to-learning-ranking-functions-for-web-search.pdf">
 * Z. Zheng, H. Zha, T. Zhang, O. Chapelle, K. Chen, and G. Sun. A General
 * Boosting Method and its Application to Learning Ranking Functions for Web
 * Search. Advances in Neural Information Processing Systems (NIPS), 2007.</a>
 * </ul>
 */
public class MultipleAdditiveTreesModel extends LTRScoringModel {

    private final HashMap<String, Integer> fname2index;
    private final List<RegressionTree> trees = new ArrayList<>();

    public class RegressionTreeNode {
        private static final float NODE_SPLIT_SLACK = 1E-6f;

        private final float value;
        private final String feature;
        private final int featureIndex;
        private final Float threshold;
        private final RegressionTreeNode left;
        private final RegressionTreeNode right;

        public RegressionTreeNode(Settings settings) {
            value = settings.getAsFloat("value", 0f);
            Float threshold = settings.getAsFloat("threshold", null);
            if (threshold != null) {
                this.threshold = threshold + NODE_SPLIT_SLACK;
            } else {
                this.threshold = null;
            }
            Set<String> names = settings.names();
            if (names.contains("left")) {
                this.left = new RegressionTreeNode(settings.getAsSettings("left"));
            } else {
                this.left = null;
            }
            if (names.contains("right")) {
                this.right = new RegressionTreeNode(settings.getAsSettings("right"));
            } else {
                this.right = null;
            }
            if (names.contains("feature")) {
                feature = settings.get("feature");
                final Integer idx = fname2index.get(feature);
                // this happens if the tree specifies a feature that does not
                // exist
                // this could be due to lambdamart building off of pre-existing
                // trees
                // that use a feature that is no longer output during feature
                // extraction
                featureIndex = (idx == null) ? -1 : idx;
            } else {
                feature = null;
                featureIndex = -1;
            }
        }

        public boolean isLeaf() {
            return feature == null;
        }

        public float score(float[] featureVector) {
            if (isLeaf()) {
                return value;
            }

            // unsupported feature (tree is looking for a feature that does not
            // exist)
            if ((featureIndex < 0) || (featureIndex >= featureVector.length)) {
                return 0f;
            }

            if (featureVector[featureIndex] <= threshold) {
                return left.score(featureVector);
            } else {
                return right.score(featureVector);
            }
        }

        public String explain(float[] featureVector) {
            if (isLeaf()) {
                return "val: " + value;
            }

            // unsupported feature (tree is looking for a feature that does not
            // exist)
            if ((featureIndex < 0) || (featureIndex >= featureVector.length)) {
                return "'" + feature + "' does not exist in FV, Return Zero";
            }

            // could store extra information about how much training data
            // supported
            // each branch and report
            // that here

            if (featureVector[featureIndex] <= threshold) {
                String rval = "'" + feature + "':" + featureVector[featureIndex] + " <= " + threshold + ", Go Left | ";
                return rval + left.explain(featureVector);
            } else {
                String rval = "'" + feature + "':" + featureVector[featureIndex] + " > " + threshold + ", Go Right | ";
                return rval + right.explain(featureVector);
            }
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            if (isLeaf()) {
                sb.append(value);
            } else {
                sb.append("(feature=").append(feature);
                sb.append(",threshold=").append(threshold.floatValue() - NODE_SPLIT_SLACK);
                sb.append(",left=").append(left);
                sb.append(",right=").append(right);
                sb.append(')');
            }
            return sb.toString();
        }

        public void validate() throws ModelException {
            if (isLeaf()) {
                if (left != null || right != null) {
                    throw new ModelException(
                        "MultipleAdditiveTreesModel tree node is leaf with left=" + left + " and right=" + right);
                }
                return;
            }
            if (null == threshold) {
                throw new ModelException("MultipleAdditiveTreesModel tree node is missing threshold");
            }
            if (null == left) {
                throw new ModelException("MultipleAdditiveTreesModel tree node is missing left");
            } else {
                left.validate();
            }
            if (null == right) {
                throw new ModelException("MultipleAdditiveTreesModel tree node is missing right");
            } else {
                right.validate();
            }
        }

    }

    public class RegressionTree {

        private final Float weight;
        private final RegressionTreeNode root;

        public RegressionTree(Settings settings) {
            this.weight = settings.getAsFloat("weight", null);
            if (settings.names().contains("root")) {
                this.root = new RegressionTreeNode(settings.getAsSettings("root"));
            } else {
                this.root = null;
            }
        }

        public float score(float[] featureVector) {
            return weight.floatValue() * root.score(featureVector);
        }

        public String explain(float[] featureVector) {
            return root.explain(featureVector);
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder();
            sb.append("(weight=").append(weight);
            sb.append(",root=").append(root);
            sb.append(")");
            return sb.toString();
        }

        public void validate() throws ModelException {
            if (weight == null) {
                throw new ModelException("MultipleAdditiveTreesModel tree doesn't contain a weight");
            }
            if (root == null) {
                throw new ModelException("MultipleAdditiveTreesModel tree doesn't contain a tree");
            } else {
                root.validate();
            }
        }
    }

    public MultipleAdditiveTreesModel(String name, List<Feature> features, List<Normalizer> norms,
        String featureStoreName, List<Feature> allFeatures, Settings params) {
        super(name, features, norms, featureStoreName, allFeatures, params);

        fname2index = new HashMap<String, Integer>();
        for (int i = 0; i < features.size(); ++i) {
            final String key = features.get(i).getName();
            fname2index.put(key, i);
        }

        for (Map.Entry<String, Settings> entry : params.getGroups("trees").entrySet()) {
            final RegressionTree rt = new RegressionTree(entry.getValue());
            this.trees.add(rt);
        }
    }

    @Override
    public void validate() throws ModelException {
        super.validate();
        if (trees.size() == 0) {
            throw new ModelException("no trees declared for model " + name);
        }
        for (RegressionTree tree : trees) {
            tree.validate();
        }
    }

    @Override
    public float score(float[] modelFeatureValuesNormalized) {
        float score = 0;
        for (final RegressionTree t : trees) {
            score += t.score(modelFeatureValuesNormalized);
        }
        return score;
    }

    // /////////////////////////////////////////
    // produces a string that looks like:
    // 40.0 = multipleadditivetreesmodel [
    // org.apache.solr.ltr.model.MultipleAdditiveTreesModel ]
    // model applied to
    // features, sum of:
    // 50.0 = tree 0 | 'matchedTitle':1.0 > 0.500001, Go Right |
    // 'this_feature_doesnt_exist' does not
    // exist in FV, Go Left | val: 50.0
    // -10.0 = tree 1 | val: -10.0
    @Override
    public Explanation explain(LeafReaderContext context, int doc, float finalScore,
        List<Explanation> featureExplanations) {
        final float[] fv = new float[featureExplanations.size()];
        int index = 0;
        for (final Explanation featureExplain : featureExplanations) {
            fv[index] = featureExplain.getValue();
            index++;
        }

        final List<Explanation> details = new ArrayList<>();
        index = 0;

        for (final RegressionTree t : trees) {
            final float score = t.score(fv);
            final Explanation p = Explanation.match(score, "tree " + index + " | " + t.explain(fv));
            details.add(p);
            index++;
        }

        return Explanation.match(finalScore, toString() + " model applied to features, sum of:", details);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder(getClass().getSimpleName());
        sb.append("(name=").append(getName());
        sb.append(",trees=[");
        for (int ii = 0; ii < trees.size(); ++ii) {
            if (ii > 0) {
                sb.append(',');
            }
            sb.append(trees.get(ii));
        }
        sb.append("])");
        return sb.toString();
    }

}
