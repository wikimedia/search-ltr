/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
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
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DisiPriorityQueue;
import org.apache.lucene.search.DisiWrapper;
import org.apache.lucene.search.DisjunctionDISIApproximation;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.mapper.Uid;
import org.elasticsearch.index.query.IndexQueryParserService;
import org.wikimedia.search.ltr.feature.Feature;
import org.wikimedia.search.ltr.feature.Feature.FeatureWeight;
import org.wikimedia.search.ltr.feature.Feature.FeatureWeight.FeatureScorer;
import org.wikimedia.search.ltr.model.LTRScoringModel;

import com.google.common.collect.Sets;

/**
 * The ranking query that is run, reranking results using the LTRScoringModel
 * algorithm
 */
public class LTRScoringQuery extends Query {

    private static final int CLASS_NAME_HASH = LTRScoringQuery.class.getName().hashCode();

    // contains a description of the model
    final private LTRScoringModel ltrScoringModel;
    final private boolean extractAllFeatures;

    // Needed for ESQueryFeature to parse queries.
    // TODO: Better way to pass this through? Probably not
    // because it's index-specific?
    IndexQueryParserService queryParserService;

    // feature logger to output the features.
    protected FeatureLogger featureLogger;
    // Map of external parameters, such as query intent, that can be used by
    // features
    protected final Settings efi;

    public LTRScoringQuery(LTRScoringModel ltrScoringModel, boolean extractAllFeatures,
        IndexQueryParserService queryParserService) {
        this(ltrScoringModel, Settings.EMPTY, extractAllFeatures, queryParserService);
    }

    public LTRScoringQuery(LTRScoringModel ltrScoringModel, Settings externalFeatureInfo, boolean extractAllFeatures,
        IndexQueryParserService queryParserService) {
        this.ltrScoringModel = ltrScoringModel;
        this.efi = externalFeatureInfo;
        this.extractAllFeatures = extractAllFeatures;
        this.queryParserService = queryParserService;
    }

    public LTRScoringModel getScoringModel() {
        return ltrScoringModel;
    }

    public void setFeatureLogger(FeatureLogger fl) {
        this.featureLogger = fl;
    }

    public FeatureLogger getFeatureLogger() {
        return featureLogger;
    }

    public Settings getExternalFeatureInfo() {
        return efi;
    }

    @Override
    public int hashCode() {
        // TODO: Does the feature logger need to be in here?
        final int prime = 31;
        int result = CLASS_NAME_HASH;
        result = (prime * result) + ((ltrScoringModel == null) ? 0 : ltrScoringModel.hashCode());
        if (efi == null) {
            result = (prime * result) + 0;
        } else {
            result = (prime * result) + efi.hashCode();
        }
        result = (prime * result) + this.toString().hashCode();
        // Direct boosting is deprecated, but AssertingIndexSearcher needs the
        // hash code to include this
        result = (prime * result) + Float.floatToIntBits(this.getBoost());
        return result;
    }

    @Override
    public boolean equals(Object o) {
        return o != null && getClass().isAssignableFrom(o.getClass()) && equalsTo(getClass().cast(o));
    }

    private boolean equalsTo(LTRScoringQuery other) {
        // TODO: Does the feature logger need to be in here?
        if (ltrScoringModel == null) {
            if (other.ltrScoringModel != null) {
                return false;
            }
        } else if (!ltrScoringModel.equals(other.ltrScoringModel)) {
            return false;
        }
        if (efi == null) {
            if (other.efi != null) {
                return false;
            }
        } else {
            if (other.efi == null || !efi.equals(other.efi)) {
                return false;
            }
        }
        if (getBoost() != other.getBoost()) {
            return false;
        }
        return true;
    }

    @Override
    public ModelWeight createWeight(IndexSearcher searcher, boolean needsScores) throws IOException {
        final Collection<Feature> modelFeatures = ltrScoringModel.getFeatures();
        final Collection<Feature> allFeatures = ltrScoringModel.getAllFeatures();
        int modelFeatSize = modelFeatures.size();

        Collection<Feature> features = null;
        if (this.extractAllFeatures) {
            features = allFeatures;
        } else {
            features = modelFeatures;
        }
        final FeatureWeight[] extractedFeatureWeights = new FeatureWeight[features.size()];
        final FeatureWeight[] modelFeaturesWeights = new FeatureWeight[modelFeatSize];
        List<FeatureWeight> featureWeights = new ArrayList<>(features.size());

        createWeights(searcher, needsScores, featureWeights, features);
        int i = 0, j = 0;
        if (this.extractAllFeatures) {
            for (final FeatureWeight fw : featureWeights) {
                extractedFeatureWeights[i++] = fw;
            }
            for (final Feature f : modelFeatures) {
                modelFeaturesWeights[j++] = extractedFeatureWeights[f.getIndex()];
                // we can lookup by featureid because all features will
                // be extracted when this.extractAllFeatures is set
            }
        } else {
            for (final FeatureWeight fw : featureWeights) {
                extractedFeatureWeights[i++] = fw;
                modelFeaturesWeights[j++] = fw;
            }
        }
        return new ModelWeight(searcher, modelFeaturesWeights, extractedFeatureWeights, allFeatures.size());
    }

    private void createWeights(IndexSearcher searcher, boolean needsScores, List<FeatureWeight> featureWeights,
        Collection<Feature> features) throws IOException {
        // since the feature store is a linkedhashmap order is preserved
        for (final Feature f : features) {
            try {
                FeatureWeight fw = f.createWeight(searcher, needsScores, efi, queryParserService);
                featureWeights.add(fw);
            } catch (final Exception e) {
                throw new RuntimeException("Exception from createWeight for " + f.toString() + " " + e.getMessage(), e);
            }
        }
    }

    @Override
    public String toString(String field) {
        return field;
    }

    public class FeatureInfo {
        String name;
        float value;
        boolean used;

        FeatureInfo(String n, float v, boolean u) {
            name = n;
            value = v;
            used = u;
        }

        public void setScore(float score) {
            this.value = score;
        }

        public String getName() {
            return name;
        }

        public float getValue() {
            return value;
        }

        public boolean isUsed() {
            return used;
        }

        public void setUsed(boolean used) {
            this.used = used;
        }
    }

    public static class IdExtractor {
        private final LeafReaderContext context;
        private static final String FIELD = "_uid";
        private static final Set<String> FIELD_AS_SET = Sets.newHashSet(FIELD);

        public IdExtractor(LeafReaderContext context) {
            this.context = context;
        }

        public Uid get(int docid) throws IOException {
            final Document document = context.reader().document(docid, FIELD_AS_SET);
            final IndexableField indexableField = document.getField(FIELD);
            if (indexableField == null) {
                return null;
            }
            return Uid.createUid(indexableField.stringValue());
        }
    }

    public class ModelWeight extends Weight {

        IndexSearcher searcher;

        // List of the model's features used for scoring. This is a subset of
        // the
        // features used for logging.
        FeatureWeight[] modelFeatureWeights;
        float[] modelFeatureValuesNormalized;
        FeatureWeight[] extractedFeatureWeights;

        // List of all the feature names, values - used for both scoring and
        // logging
        /*
         * What is the advantage of using a hashmap here instead of an array of
         * objects? A set of arrays was used earlier and the elements were
         * accessed using the featureId. With the updated logic to create
         * weights selectively, the number of elements in the array can be fewer
         * than the total number of features. When [features] are not requested,
         * only the model features are extracted. In this case, the indexing by
         * featureId, fails. For this reason, we need a map which holds just the
         * features that were triggered by the documents in the result set.
         *
         */
        FeatureInfo[] featuresInfo;

        /*
         * @param modelFeatureWeights - should be the same size as the number of
         * features used by the model
         *
         * @param extractedFeatureWeights - if features are requested from the
         * same store as model feature store, this will be the size of total
         * number of features in the model feature store else, this will be the
         * size of the modelFeatureWeights
         *
         * @param allFeaturesSize - total number of feature in the feature store
         * used by this model
         */
        public ModelWeight(IndexSearcher searcher, FeatureWeight[] modelFeatureWeights,
            FeatureWeight[] extractedFeatureWeights, int allFeaturesSize) {
            super(LTRScoringQuery.this);
            this.searcher = searcher;
            this.extractedFeatureWeights = extractedFeatureWeights;
            this.modelFeatureWeights = modelFeatureWeights;
            this.modelFeatureValuesNormalized = new float[modelFeatureWeights.length];
            this.featuresInfo = new FeatureInfo[allFeaturesSize];
            setFeaturesInfo();
        }

        private void setFeaturesInfo() {
            for (int i = 0; i < extractedFeatureWeights.length; ++i) {
                String featName = extractedFeatureWeights[i].getName();
                int featId = extractedFeatureWeights[i].getIndex();
                float value = extractedFeatureWeights[i].getDefaultValue();
                featuresInfo[featId] = new FeatureInfo(featName, value, false);
            }
        }

        public FeatureInfo[] getFeaturesInfo() {
            return featuresInfo;
        }

        // for test use
        Feature.FeatureWeight[] getModelFeatureWeights() {
            return modelFeatureWeights;
        }

        // for test use
        float[] getModelFeatureValuesNormalized() {
            return modelFeatureValuesNormalized;
        }

        // for test use
        Feature.FeatureWeight[] getExtractedFeatureWeights() {
            return extractedFeatureWeights;
        }

        @Override
        public float getValueForNormalization() throws IOException {
            return 1.0f;
        }

        @Override
        public void normalize(float norm, float boost) {
            // Intentionally ignored
        }

        /**
         * Goes through all the stored feature values, and calculates the
         * normalized values for all the features that will be used for scoring.
         */
        private void makeNormalizedFeatures() {
            int pos = 0;
            for (final FeatureWeight feature : modelFeatureWeights) {
                final int featureId = feature.getIndex();
                FeatureInfo fInfo = featuresInfo[featureId];
                if (fInfo.isUsed()) { // not checking for finfo == null as that
                                      // would be a bug we should catch
                    modelFeatureValuesNormalized[pos] = fInfo.getValue();
                } else {
                    modelFeatureValuesNormalized[pos] = feature.getDefaultValue();
                }
                pos++;
            }
            ltrScoringModel.normalizeFeaturesInPlace(modelFeatureValuesNormalized);
        }

        @Override
        public Explanation explain(LeafReaderContext context, int doc) throws IOException {

            final Explanation[] explanations = new Explanation[this.featuresInfo.length];
            for (final FeatureWeight feature : extractedFeatureWeights) {
                explanations[feature.getIndex()] = feature.explain(context, doc);
            }
            final List<Explanation> featureExplanations = new ArrayList<>();
            for (int idx = 0; idx < modelFeatureWeights.length; ++idx) {
                final FeatureWeight f = modelFeatureWeights[idx];
                Explanation e = ltrScoringModel.getNormalizerExplanation(explanations[f.getIndex()], idx);
                featureExplanations.add(e);
            }
            final ModelScorer bs = scorer(context, false);
            bs.iterator().advance(doc);

            final float finalScore = bs.score();

            return ltrScoringModel.explain(context, doc, finalScore, featureExplanations);

        }

        @Override
        public void extractTerms(Set<Term> terms) {
            for (final FeatureWeight feature : extractedFeatureWeights) {
                feature.extractTerms(terms);
            }
        }

        protected void reset() {
            for (int i = 0; i < extractedFeatureWeights.length; ++i) {
                int featId = extractedFeatureWeights[i].getIndex();
                float value = extractedFeatureWeights[i].getDefaultValue();
                // need to set default value everytime as
                // the default value is used in 'dense' mode
                // even if used=false
                featuresInfo[featId].setScore(value);
                featuresInfo[featId].setUsed(false);
            }
        }

        @Override
        public ModelScorer scorer(LeafReaderContext context) throws IOException {
            return scorer(context, true);
        }

        public ModelScorer scorer(LeafReaderContext context, boolean logFeatures) throws IOException {

            final List<FeatureScorer> featureScorers = new ArrayList<FeatureScorer>(extractedFeatureWeights.length);
            for (final FeatureWeight featureWeight : extractedFeatureWeights) {
                final FeatureScorer scorer = featureWeight.scorer(context);
                if (scorer != null) {
                    featureScorers.add(featureWeight.scorer(context));
                }
            }
            IdExtractor idExtractor = null;
            if (logFeatures == true && featureLogger != null) {
                idExtractor = new IdExtractor(context);
            }

            // Always return a ModelScorer, even if no features match, because
            // we
            // always need to call
            // score on the model for every document, since 0 features matching
            // could
            // return a
            // non 0 score for a given model.
            ModelScorer mscorer = new ModelScorer(this, featureScorers, idExtractor);
            return mscorer;

        }

        public class ModelScorer extends Scorer {
            final private Scorer featureTraversalScorer;
            final private IdExtractor idExtractor;

            public ModelScorer(Weight weight, List<FeatureScorer> featureScorers, IdExtractor idExtractor) {
                super(weight);
                this.idExtractor = idExtractor;
                // TODO: Allow the use of dense
                // features in other cases
                if (featureScorers.size() <= 1) {
                    featureTraversalScorer = new DenseModelScorer(weight, featureScorers);
                } else {
                    featureTraversalScorer = new SparseModelScorer(weight, featureScorers);
                }
            }

            @Override
            public Collection<ChildScorer> getChildren() {
                return featureTraversalScorer.getChildren();
            }

            @Override
            public int docID() {
                return featureTraversalScorer.docID();
            }

            @Override
            public float score() throws IOException {
                return featureTraversalScorer.score();
            }

            @Override
            public int freq() throws IOException {
                return featureTraversalScorer.freq();
            }

            @Override
            public DocIdSetIterator iterator() {
                return featureTraversalScorer.iterator();
            }

            public class SparseModelScorer extends Scorer {
                protected DisiPriorityQueue subScorers;
                protected ScoringQuerySparseIterator itr;

                protected int targetDoc = -1;
                protected int activeDoc = -1;

                protected SparseModelScorer(Weight weight, List<FeatureScorer> featureScorers) {
                    super(weight);
                    if (featureScorers.size() <= 1) {
                        throw new IllegalArgumentException("There must be at least 2 subScorers");
                    }
                    subScorers = new DisiPriorityQueue(featureScorers.size());
                    for (final Scorer scorer : featureScorers) {
                        final DisiWrapper w = new DisiWrapper(scorer);
                        subScorers.add(w);
                    }

                    itr = new ScoringQuerySparseIterator(subScorers);
                }

                @Override
                public int docID() {
                    return itr.docID();
                }

                @Override
                public float score() throws IOException {
                    final DisiWrapper topList = subScorers.topList();
                    // If target doc we wanted to advance to matches the actual
                    // doc
                    // the underlying features advanced to, perform the feature
                    // calculations,
                    // otherwise just continue with the model's scoring process
                    // with empty
                    // features.
                    reset();
                    if (activeDoc == targetDoc) {
                        for (DisiWrapper w = topList; w != null; w = w.next) {
                            final Scorer subScorer = w.scorer;
                            FeatureWeight scFW = (FeatureWeight) subScorer.getWeight();
                            final int featureId = scFW.getIndex();
                            featuresInfo[featureId].setScore(subScorer.score());
                            featuresInfo[featureId].setUsed(true);
                        }
                    }
                    makeNormalizedFeatures();
                    if (featureLogger != null && idExtractor != null) {
                        featureLogger.log(idExtractor.get(docID()), featuresInfo);
                    }
                    return ltrScoringModel.score(modelFeatureValuesNormalized);
                }

                @Override
                public int freq() throws IOException {
                    final DisiWrapper subMatches = subScorers.topList();
                    int freq = 1;
                    for (DisiWrapper w = subMatches.next; w != null; w = w.next) {
                        freq += 1;
                    }
                    return freq;
                }

                @Override
                public DocIdSetIterator iterator() {
                    return itr;
                }

                @Override
                public final Collection<ChildScorer> getChildren() {
                    final ArrayList<ChildScorer> children = new ArrayList<>();
                    for (final DisiWrapper scorer : subScorers) {
                        children.add(new ChildScorer(scorer.scorer, "SHOULD"));
                    }
                    return children;
                }

                protected class ScoringQuerySparseIterator extends DisjunctionDISIApproximation {

                    public ScoringQuerySparseIterator(DisiPriorityQueue subIterators) {
                        super(subIterators);
                    }

                    @Override
                    public final int nextDoc() throws IOException {
                        if (activeDoc == targetDoc) {
                            activeDoc = super.nextDoc();
                        } else if (activeDoc < targetDoc) {
                            activeDoc = super.advance(targetDoc + 1);
                        }
                        return ++targetDoc;
                    }

                    @Override
                    public final int advance(int target) throws IOException {
                        // If target doc we wanted to advance to matches the
                        // actual doc
                        // the underlying features advanced to, perform the
                        // feature
                        // calculations,
                        // otherwise just continue with the model's scoring
                        // process with
                        // empty features.
                        if (activeDoc < target) {
                            activeDoc = super.advance(target);
                        }
                        targetDoc = target;
                        return targetDoc;
                    }
                }

            }

            public class DenseModelScorer extends Scorer {
                int activeDoc = -1; // The doc that our scorer's are actually at
                int targetDoc = -1; // The doc we were most recently told to go
                                    // to
                int freq = -1;
                List<FeatureScorer> featureScorers;

                protected DenseModelScorer(Weight weight, List<FeatureScorer> featureScorers) {
                    super(weight);
                    this.featureScorers = featureScorers;
                }

                @Override
                public int docID() {
                    return targetDoc;
                }

                @Override
                public float score() throws IOException {
                    reset();
                    freq = 0;
                    if (targetDoc == activeDoc) {
                        for (final Scorer scorer : featureScorers) {
                            if (scorer.docID() == activeDoc) {
                                freq++;
                                FeatureWeight scFW = (FeatureWeight) scorer.getWeight();
                                final int featureId = scFW.getIndex();
                                featuresInfo[featureId].setScore(scorer.score());
                                featuresInfo[featureId].setUsed(true);
                            }
                        }
                    }
                    makeNormalizedFeatures();
                    return ltrScoringModel.score(modelFeatureValuesNormalized);
                }

                @Override
                public final Collection<ChildScorer> getChildren() {
                    final ArrayList<ChildScorer> children = new ArrayList<>();
                    for (final Scorer scorer : featureScorers) {
                        children.add(new ChildScorer(scorer, "SHOULD"));
                    }
                    return children;
                }

                @Override
                public int freq() throws IOException {
                    return freq;
                }

                @Override
                public DocIdSetIterator iterator() {
                    return new DenseIterator();
                }

                class DenseIterator extends DocIdSetIterator {

                    @Override
                    public int docID() {
                        return targetDoc;
                    }

                    @Override
                    public int nextDoc() throws IOException {
                        if (activeDoc <= targetDoc) {
                            activeDoc = NO_MORE_DOCS;
                            for (final Scorer scorer : featureScorers) {
                                if (scorer.docID() != NO_MORE_DOCS) {
                                    activeDoc = Math.min(activeDoc, scorer.iterator().nextDoc());
                                }
                            }
                        }
                        return ++targetDoc;
                    }

                    @Override
                    public int advance(int target) throws IOException {
                        if (activeDoc < target) {
                            activeDoc = NO_MORE_DOCS;
                            for (final Scorer scorer : featureScorers) {
                                if (scorer.docID() != NO_MORE_DOCS) {
                                    activeDoc = Math.min(activeDoc, scorer.iterator().advance(target));
                                }
                            }
                        }
                        targetDoc = target;
                        return target;
                    }

                    @Override
                    public long cost() {
                        long sum = 0;
                        for (int i = 0; i < featureScorers.size(); i++) {
                            sum += featureScorers.get(i).iterator().cost();
                        }
                        return sum;
                    }
                }
            }
        }
    }
}
