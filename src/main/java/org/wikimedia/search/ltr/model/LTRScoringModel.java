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

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.Explanation;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.settings.Settings;
import org.wikimedia.search.ltr.feature.Feature;
import org.wikimedia.search.ltr.feature.FeatureException;
import org.wikimedia.search.ltr.norm.IdentityNormalizer;
import org.wikimedia.search.ltr.norm.Normalizer;

/**
 * A scoring model computes scores that can be used to rerank documents.
 * <p>
 * A scoring model consists of
 * <ul>
 * <li>a list of features ({@link Feature}) and
 * <li>a list of normalizers ({@link Normalizer}) plus
 * <li>parameters or configuration to represent the scoring algorithm.
 * </ul>
 * <p>
 * Example configuration (snippet):
 *
 * <pre>
 * {
   "class" : "...",
   "name" : "myModelName",
   "features" : [
       {
         "name" : "isBook"
       },
       {
         "name" : "originalScore",
         "norm": {
             "class" : "org.wikimedia.search.ltr.norm.StandardNormalizer",
             "params" : { "avg":"100", "std":"10" }
         }
       },
       {
         "name" : "price",
         "norm": {
             "class" : "org.wikimedia.search.ltr.norm.MinMaxNormalizer",
             "params" : { "min":"0", "max":"1000" }
         }
       }
   ],
   "params" : {
       ...
   }
}
 * </pre>
 * <p>
 * {@link LTRScoringModel} is an abstract class and concrete classes must
 * implement the {@link #score(float[])} and
 * {@link #explain(LeafReaderContext, int, float, List)} methods.
 */
public abstract class LTRScoringModel {
    private static ESLogger log = ESLoggerFactory.getLogger(LTRScoringModel.class.getCanonicalName());

    protected final String name;
    private final String featureStoreName;
    protected final List<Feature> features;
    private final List<Feature> allFeatures;
    private final Settings params;
    private final List<Normalizer> norms;

    public static LTRScoringModel getInstance(String className, String name, List<Feature> features,
        List<Normalizer> norms, String featureStoreName, List<Feature> allFeatures, Settings params)
        throws ModelException {
        Class<?> clazz;
        try {
            clazz = LTRScoringModel.class.getClassLoader().loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new ModelException("Model type does not exist " + className, e);
        }
        if (!LTRScoringModel.class.isAssignableFrom(clazz)) {
            throw new ModelException("Model type is not an LTRScoringModel: " + className);
        }
        // create an instance of the model
        LTRScoringModel model;
        try {
            model = (LTRScoringModel) clazz
                .getConstructor(String.class, List.class, List.class, String.class, List.class, Settings.class)
                .newInstance(name, features, norms, featureStoreName, allFeatures, params);
        } catch (NoSuchMethodException e) {
            throw new ModelException("Model type does not have valid constructor: " + className, e);
        } catch (InvocationTargetException e) {
            log.error("Model type failed construction [" + className + "]", e);
            throw new ModelException("Model type failed construction [" + className + "] [" + e.getCause() + "]", e);
        } catch (Exception e) {
            throw new ModelException("Model type failed construction [" + className + "] [" + e + "]", e);
        }
        model.validate();
        return model;
    }

    public LTRScoringModel(String name, List<Feature> features, List<Normalizer> norms, String featureStoreName,
        List<Feature> allFeatures, Settings params) {
        this.name = name;
        this.features = features;
        this.featureStoreName = featureStoreName;
        this.allFeatures = allFeatures;
        this.params = params;
        this.norms = norms;
    }

    /**
     * Validate that settings make sense and throws {@link ModelException} if
     * they do not make sense.
     */
    public void validate() throws ModelException {
        if (features.isEmpty()) {
            throw new ModelException("no features declared for model " + name);
        }
        final HashSet<String> featureNames = new HashSet<>();
        for (final Feature feature : features) {
            final String featureName = feature.getName();
            if (!featureNames.add(featureName)) {
                throw new ModelException("duplicated feature " + featureName + " in model " + name);
            }
        }
        if (features.size() != norms.size()) {
            throw new ModelException(
                "counted " + features.size() + " features and " + norms.size() + " norms in model " + name);
        }
    }

    /**
     * @return the norms
     */
    public List<Normalizer> getNorms() {
        return Collections.unmodifiableList(norms);
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the features
     */
    public List<Feature> getFeatures() {
        return Collections.unmodifiableList(features);
    }

    public Settings getParams() {
        return params;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = (prime * result) + ((features == null) ? 0 : features.hashCode());
        result = (prime * result) + ((name == null) ? 0 : name.hashCode());
        result = (prime * result) + ((params == null) ? 0 : params.hashCode());
        result = (prime * result) + ((norms == null) ? 0 : norms.hashCode());
        result = (prime * result) + ((featureStoreName == null) ? 0 : featureStoreName.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final LTRScoringModel other = (LTRScoringModel) obj;
        if (features == null) {
            if (other.features != null) {
                return false;
            }
        } else if (!features.equals(other.features)) {
            return false;
        }
        if (norms == null) {
            if (other.norms != null) {
                return false;
            }
        } else if (!norms.equals(other.norms)) {
            return false;
        }
        if (name == null) {
            if (other.name != null) {
                return false;
            }
        } else if (!name.equals(other.name)) {
            return false;
        }
        if (params == null) {
            if (other.params != null) {
                return false;
            }
        } else if (!params.equals(other.params)) {
            return false;
        }
        if (featureStoreName == null) {
            if (other.featureStoreName != null) {
                return false;
            }
        } else if (!featureStoreName.equals(other.featureStoreName)) {
            return false;
        }

        return true;
    }

    public boolean hasParams() {
        return params.getAsStructuredMap().size() > 0;
    }

    public Collection<Feature> getAllFeatures() {
        return allFeatures;
    }

    public String getFeatureStoreName() {
        return featureStoreName;
    }

    /**
     * Given a list of normalized values for all features a scoring algorithm
     * cares about, calculate and return a score.
     *
     * @param modelFeatureValuesNormalized
     *            List of normalized feature values. Each feature is identified
     *            by its id, which is the index in the array
     * @return The final score for a document
     */
    public abstract float score(float[] modelFeatureValuesNormalized);

    /**
     * Similar to the score() function, except it returns an explanation of how
     * the features were used to calculate the score.
     *
     * @param context
     *            Context the document is in
     * @param doc
     *            Document to explain
     * @param finalScore
     *            Original score
     * @param featureExplanations
     *            Explanations for each feature calculation
     * @return Explanation for the scoring of a document
     */
    public abstract Explanation explain(LeafReaderContext context, int doc, float finalScore,
        List<Explanation> featureExplanations);

    @Override
    public String toString() {
        return getClass().getSimpleName() + "(name=" + getName() + ")";
    }

    /**
     * Goes through all the stored feature values, and calculates the normalized
     * values for all the features that will be used for scoring.
     */
    public void normalizeFeaturesInPlace(float[] modelFeatureValues) {
        float[] modelFeatureValuesNormalized = modelFeatureValues;
        if (modelFeatureValues.length != norms.size()) {
            throw new FeatureException("Must have normalizer for every feature");
        }
        for (int idx = 0; idx < modelFeatureValuesNormalized.length; ++idx) {
            modelFeatureValuesNormalized[idx] = norms.get(idx).normalize(modelFeatureValuesNormalized[idx]);
        }
    }

    public Explanation getNormalizerExplanation(Explanation e, int idx) {
        Normalizer n = norms.get(idx);
        if (n != IdentityNormalizer.INSTANCE) {
            return n.explain(e);
        }
        return e;
    }

}
