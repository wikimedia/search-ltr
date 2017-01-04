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

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.IndexQueryParserService;
import org.elasticsearch.search.lookup.SourceLookup;

import com.google.common.collect.Sets;

/**
 * This feature returns the value of a field in the current document. Currently
 * this field must be a stored field, and not part of the _source.
 *
 * Example configuration:
 *
 * <pre>
 * {
  "name":  "rawHits",
  "class": "org.wikimedia.search.ltr.feature.FieldValueFeature",
  "params": {
      "field": "hits"
  }
}
 * </pre>
 */
public class FieldValueFeature extends Feature {

    private final String field;
    private final Set<String> fieldAsSet;
    private final SourceLookup sourceLookup;

    public String getField() {
        return field;
    }

    @Override
    public LinkedHashMap<String, Object> paramsToMap() {
        final LinkedHashMap<String, Object> params = new LinkedHashMap<>(1, 1.0f);
        params.put("field", field);
        if (sourceLookup != null) {
            params.put("source", "true");
        }
        return params;
    }

    @Override
    protected void validate() throws FeatureException {
        if (field == null || field.isEmpty()) {
            throw new FeatureException(getClass().getSimpleName() + ": field must be provided");
        }
    }

    public FieldValueFeature(String name, Settings params) {
        super(name, params);
        field = params.get("field", null);
        if (field == null) {
            throw new FeatureException(getClass().getSimpleName() + ": field must be provided");
        }
        if (params.getAsBoolean("source", false) == true) {
            fieldAsSet = null;
            sourceLookup = new SourceLookup();
        } else {
            fieldAsSet = Sets.newHashSet(field);
            sourceLookup = null;
        }
    }

    @Override
    public FeatureWeight createWeight(IndexSearcher searcher, boolean needsScores, Settings efi,
        IndexQueryParserService queryParserService) throws IOException {
        return new FieldValueFeatureWeight(searcher, efi);
    }

    public class FieldValueFeatureWeight extends FeatureWeight {

        public FieldValueFeatureWeight(IndexSearcher searcher, Settings efi) {
            super(FieldValueFeature.this, searcher, efi);
        }

        @Override
        public FeatureScorer scorer(LeafReaderContext context) throws IOException {
            return new FieldValueFeatureScorer(this, context, DocIdSetIterator.all(DocIdSetIterator.NO_MORE_DOCS));
        }

        public class FieldValueFeatureScorer extends FeatureScorer {

            LeafReaderContext context = null;

            public FieldValueFeatureScorer(FeatureWeight weight, LeafReaderContext context, DocIdSetIterator itr) {
                super(weight, itr);
                this.context = context;
            }

            @Override
            public float score() throws IOException {

                try {
                    Number number = null;
                    if (sourceLookup == null) {
                        final Document document = context.reader().document(itr.docID(), fieldAsSet);
                        final IndexableField indexableField = document.getField(field);
                        if (indexableField == null) {
                            return getDefaultValue();
                        }
                        number = indexableField.numericValue();
                    } else {
                        sourceLookup.setSegmentAndDocument(context, itr.docID());
                        final Object value = sourceLookup.extractValue(field);
                        if (value != null && Number.class.isAssignableFrom(value.getClass())) {
                            number = (Number) value;
                        }
                    }
                    if (number != null) {
                        return number.floatValue();
                    } else {
                        /*
                         * final String string = indexableField.stringValue();
                         * // boolean values in the index are encoded with the
                         * // chars T/F if (string.equals("T")) { return 1; } if
                         * (string.equals("F")) { return 0; }
                         */
                    }
                } catch (final IOException e) {
                    throw new FeatureException(e.toString() + ": " + "Unable to extract feature for " + name, e);
                }
                return getDefaultValue();
            }
        }
    }
}
