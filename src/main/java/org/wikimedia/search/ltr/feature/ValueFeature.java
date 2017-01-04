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

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.IndexQueryParserService;

/**
 * This feature allows to return a constant given value for the current
 * document.
 *
 * Example configuration:
 *
 * <pre>
 * {
   "name" : "userFromMobile",
   "class" : "org.wikimedia.search.ltr.feature.ValueFeature",
   "params" : { "value" : "42", "required":true }
 }
 * </pre>
 *
 * You can place a constant value like "1.3f" in the value params. Not sure the
 * use cases for this one, but it's used in the unit tests for simplicity.
 **/
public class ValueFeature extends Feature {
    private Float configValue = -1f;

    public ValueFeature(String name, Settings params) {
        super(name, params);
        configValue = params.getAsFloat("value", null);
    }

    @Override
    protected void validate() throws FeatureException {
        if (configValue == null) {
            throw new FeatureException("No 'value' param provided");
        }
    }

    public float getValue() {
        return configValue;
    }

    @Override
    public LinkedHashMap<String, Object> paramsToMap() {
        final LinkedHashMap<String, Object> params = new LinkedHashMap<>(2, 1.0f);
        params.put("value", configValue);
        return params;
    }

    @Override
    public FeatureWeight createWeight(IndexSearcher searcher, boolean needsScores, Settings efi,
        IndexQueryParserService queryParserService) throws IOException {
        return new ValueFeatureWeight(searcher, efi);
    }

    public class ValueFeatureWeight extends FeatureWeight {

        public ValueFeatureWeight(IndexSearcher searcher, Settings efi) {
            super(ValueFeature.this, searcher, efi);
        }

        @Override
        public FeatureScorer scorer(LeafReaderContext context) throws IOException {
            return new ValueFeatureScorer(this, configValue, DocIdSetIterator.all(DocIdSetIterator.NO_MORE_DOCS));
        }
    }
}
