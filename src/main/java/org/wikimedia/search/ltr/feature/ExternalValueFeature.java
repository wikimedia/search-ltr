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
 * This feature allows to return a constant given value from external feature
 * info for the current document.
 *
 * Example configuration:
 *
 * <pre>
 * {
   "name" : "userFromMobile",
   "class" : "org.wikimedia.search.ltr.feature.ExternalValueFeature",
   "params" : { "externalValue" : "userFromMobile", "required":true }
 }
 * </pre>
 *
 * many times you would want to pass in external information to use per request.
 * For instance, maybe you want to rank things differently if the search came
 * from a mobile device, or maybe you want to use your external query intent
 * system as a feature. In the ltr query you can pass in "efi": {
 * "userFromMobile": 1 } and the above feature will return 1 for all the docs
 * for that request. If required is set to true, the request will return an
 * error since you failed to pass in the efi, otherwise if will just skip the
 * feature and use a default value of 0 instead.
 **/
public class ExternalValueFeature extends Feature {
    private final String externalValue;
    private final boolean required;

    public ExternalValueFeature(String name, Settings params) {
        super(name, params);
        externalValue = params.get("externalValue");
        required = params.getAsBoolean("required", false);
    }

    @Override
    public LinkedHashMap<String, Object> paramsToMap() {
        final LinkedHashMap<String, Object> params = new LinkedHashMap<>(2, 1.0f);
        params.put("externalValue", externalValue);
        if (required != false) {
            params.put("required", required);
        }
        return params;
    }

    @Override
    public FeatureWeight createWeight(IndexSearcher searcher, boolean needsScores, Settings efi,
        IndexQueryParserService queryParserService) throws IOException {
        return new ValueFeatureWeight(searcher, efi);
    }

    public class ValueFeatureWeight extends FeatureWeight {
        final protected Float featureValue;

        public ValueFeatureWeight(IndexSearcher searcher, Settings efi) {
            super(ExternalValueFeature.this, searcher, efi);
            featureValue = efi.getAsFloat(externalValue, null);
            if (featureValue == null && required) {
                throw new FeatureException(
                    this.getClass().getSimpleName() + " requires efi parameter that was not passed in request.");
            }
        }

        @Override
        public FeatureScorer scorer(LeafReaderContext context) throws IOException {
            if (featureValue != null)
                return new ValueFeatureScorer(this, featureValue, DocIdSetIterator.all(DocIdSetIterator.NO_MORE_DOCS));
            else
                return null;
        }
    }
}
