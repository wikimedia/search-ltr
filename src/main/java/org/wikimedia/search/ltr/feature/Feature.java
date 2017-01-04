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
package org.wikimedia.search.ltr.feature;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Set;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.IndexQueryParserService;

/**
 * A recipe for computing a feature. Subclass this for specialized feature
 * calculations.
 * <p>
 * A feature consists of
 * <ul>
 * <li>a name as the identifier
 * <li>parameters to represent the specific feature
 * </ul>
 * <p>
 * Example configuration (snippet):
 *
 * <pre>
 * {
   "class" : "...",
   "name" : "myFeature",
   "params" : {
       ...
   }
}
 * </pre>
 * <p>
 * {@link Feature} is an abstract class and concrete classes should implement
 * the {@link #validate()} function, and must implement the
 * {@link #paramsToMap()} and createWeight() methods.
 */
public abstract class Feature extends Query {
    final protected String name;
    private int index = -1;
    private float defaultValue = 0.0f;

    final private Settings params;
    private static final int CLASS_NAME_HASH = Feature.class.getName().hashCode();

    public static Feature getInstance(String className, String name, Settings params) {
        Class<?> clazz;
        if (className == null) {
            throw new FeatureException("No class name provided");
        }
        try {
            clazz = Feature.class.getClassLoader().loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new FeatureException("Feature type does not exist " + className, e);
        }
        if (!Feature.class.isAssignableFrom(clazz)) {
            throw new FeatureException("Feature type is not a Feature " + className);
        }
        Feature f;
        try {
            f = (Feature) clazz.getConstructor(String.class, Settings.class).newInstance(name, params);
        } catch (NoSuchMethodException e) {
            throw new FeatureException("Feature type does not have valid constructor: " + className, e);
        } catch (Exception e) {
            throw new FeatureException("Feature type failed construction: " + className, e);
        }
        f.validate();
        return f;
    }

    public Feature(String name, Settings params) {
        this.name = name;
        this.params = params;
        this.defaultValue = params.getAsFloat("defaultValue", 0f);
    }

    /**
     * On construction of a feature, this function confirms that the feature
     * parameters are validated
     *
     * @throws FeatureException
     *             Feature Exception
     */
    protected void validate() throws FeatureException {

    }

    @Override
    public String toString(String field) {
        // default initialCapacity of 16 won't be enough
        final StringBuilder sb = new StringBuilder(64);
        sb.append(getClass().getSimpleName());
        sb.append(" [name=").append(name);
        final LinkedHashMap<String, Object> params = paramsToMap();
        if (params != null) {
            sb.append(", params=").append(params);
        }
        sb.append(']');
        return sb.toString();
    }

    public abstract FeatureWeight createWeight(IndexSearcher searcher, boolean needsScores, Settings efi,
        IndexQueryParserService queryParserService) throws IOException;

    public float getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String value) {
        defaultValue = Float.parseFloat(value);
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = CLASS_NAME_HASH;
        result = (prime * result) + index;
        result = (prime * result) + ((name == null) ? 0 : name.hashCode());
        result = (prime * result) + ((params == null) ? 0 : params.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object o) {
        return o != null && Feature.class.isAssignableFrom(o.getClass()) && equalsTo(getClass().cast(o));
    }

    private boolean equalsTo(Feature other) {
        if (index != other.index) {
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
        return true;
    }

    /**
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @return the id
     */
    public int getIndex() {
        return index;
    }

    /**
     * @param index
     *            Unique ID for this feature. Similar to feature name, except it
     *            can be used to directly access the feature in the global list
     *            of features.
     */
    public void setIndex(int index) {
        this.index = index;
    }

    public abstract LinkedHashMap<String, Object> paramsToMap();

    /**
     * Weight for a feature
     **/
    public abstract class FeatureWeight extends Weight {

        final protected IndexSearcher searcher;
        final protected Settings efi;

        /**
         * Initialize a feature without the normalizer from the feature file.
         * This is called on initial construction since multiple models share
         * the same features, but have different normalizers. A concrete model's
         * feature is copied through featForNewModel().
         *
         * @param q
         *            Solr query associated with this FeatureWeight
         * @param searcher
         *            Solr searcher available for features if they need them
         */
        public FeatureWeight(Query q, IndexSearcher searcher, Settings efi) {
            super(q);
            this.searcher = searcher;
            this.efi = efi;
        }

        public String getName() {
            return Feature.this.getName();
        }

        public int getIndex() {
            return Feature.this.getIndex();
        }

        public float getDefaultValue() {
            return Feature.this.getDefaultValue();
        }

        @Override
        public float getValueForNormalization() {
            return 1.0f;
        }

        @Override
        public abstract FeatureScorer scorer(LeafReaderContext context) throws IOException;

        @Override
        public Explanation explain(LeafReaderContext context, int doc) throws IOException {
            final FeatureScorer r = scorer(context);
            float score = getDefaultValue();
            if (r != null) {
                r.iterator().advance(doc);
                if (r.docID() == doc)
                    score = r.score();
                return Explanation.match(score, toString());
            } else {
                return Explanation.match(score, "The feature has no value");
            }
        }

        @Override
        public void normalize(float norm, float boost) {
            // Intentionally ignored
        }

        /**
         * Used in the FeatureWeight's explain. Each feature should implement
         * this returning properties of the specific scorer useful for an
         * explain. For example "MyCustomClassFeature [name=" + name +
         * "myVariable:" + myVariable + "]"; If not provided, a default
         * implementation will return basic feature properties, which might not
         * include query time specific values.
         */
        @Override
        public String toString() {
            return Feature.this.toString();
        }

        @Override
        public void extractTerms(Set<Term> terms) {
            // Is there any need for this? It prevents using LTRScoringQuery
            // outside the rescore window (mostly for testing).
            // throw new UnsupportedOperationException();
        }

        /**
         * A 'recipe' for computing a feature
         */
        public abstract class FeatureScorer extends Scorer {

            final protected String name;
            protected DocIdSetIterator itr;

            public FeatureScorer(Feature.FeatureWeight weight, DocIdSetIterator itr) {
                super(weight);
                this.itr = itr;
                name = weight.getName();
            }

            @Override
            public abstract float score() throws IOException;

            @Override
            public int freq() throws IOException {
                throw new UnsupportedOperationException();
            }

            @Override
            public int docID() {
                return itr.docID();
            }

            @Override
            public DocIdSetIterator iterator() {
                return itr;
            }
        }

        /**
         * Default FeatureScorer class that returns the score passed in. Can be
         * used as a simple ValueFeature, or to return a default scorer in case
         * an underlying feature's scorer is null.
         */
        public class ValueFeatureScorer extends FeatureScorer {
            float constScore;

            public ValueFeatureScorer(FeatureWeight weight, float constScore, DocIdSetIterator itr) {
                super(weight, itr);
                this.constScore = constScore;
            }

            @Override
            public float score() {
                return constScore;
            }

        }

    }

}
