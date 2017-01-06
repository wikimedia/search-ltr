/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.	See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.	You may obtain a copy of the License at
 *
 *		 http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wikimedia.search.ltr.feature;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.Weight;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.query.IndexQueryParserService;
import org.elasticsearch.index.query.ParsedQuery;

/**
 * This feature allows you to reuse any ES query as a feature. The value of the
 * feature will be the score of the given query for the current document. See
 * <a href="https://cwiki.apache.org/confluence/display/solr/Other+Parsers">Solr
 * documentation of other parsers</a> you can use as a feature. Example
 * configurations:
 *
 * <pre>
	[{ "name": "isPerson",
		"class": "org.wikimedia.search.ltr.feature.ESQueryFeature",
		"params":{
			"q": "{\"term\": { \"category\": \"person\" } }"
		}
	},
	{
		"name":	"documentRecency",
		"class": "org.wikimedia.search.ltr.feature.ESQueryFeature",
		"params": {
			"q": "{!func}recip( ms(NOW,publish_date), 3.16e-11, 1, 1)"
		}
	}]
 * </pre>
 **/
public class ESQueryFeature extends Feature {
    private static final Pattern requiredRegex = Pattern.compile("\\G.*\\$\\{([\\w\\d]+)\\}");

    private final String q;
    private final List<String> requiredEfi = new ArrayList<>();

    public ESQueryFeature(String name, Settings params) throws IOException {
        super(name, params);
        // Turn into json so we can string-replace the EFI parameters.
        // TODO: Why isn't this pre-wrapped in an object?
        final XContentBuilder builder = JsonXContent.contentBuilder().startObject();
        q = params.getAsSettings("q").toXContent(builder, ToXContent.EMPTY_PARAMS).string();

        Matcher m = requiredRegex.matcher(q);
        while (m.find()) {
            requiredEfi.add(m.group(1));
        }
    }

    @Override
    public LinkedHashMap<String, Object> paramsToMap() {
        final LinkedHashMap<String, Object> params = new LinkedHashMap<>(3, 1.0f);
        if (q != null) {
            params.put("q", q);
        }
        return params;
    }

    @Override
    public FeatureWeight createWeight(IndexSearcher searcher, boolean needsScores, Settings efi,
        IndexQueryParserService queryParserService) throws IOException {
        return new ESQueryFeatureWeight(searcher, efi, queryParserService);
    }

    @Override
    protected void validate() throws FeatureException {
        super.validate();
        if (q == null || q.isEmpty()) {
            throw new FeatureException(getClass().getSimpleName() + ": Q  must be provided");
        }
        // TODO: Test q is parsable. Need access to IndexQueryParserService to
        // do that though, so maybe not possible?
    }

    /**
     * Weight for a ESQueryFeature
     **/
    public class ESQueryFeatureWeight extends FeatureWeight {
        Weight esQueryWeight;
        Query query;

        public ESQueryFeatureWeight(IndexSearcher searcher, Settings efi, IndexQueryParserService queryParserService)
            throws IOException {
            super(ESQueryFeature.this, searcher, efi);
            // TODO: more efficient? Use TemplateQuery somehow?
            String replaced = q;
            for (final String key : requiredEfi) {
                final String value = efi.get(key);
                if (value == null) {
                    throw new FeatureException("ESQueryFeature requires efi parameter that was not passed in request.");
                }
                // value.replace() ensures the resulting string is still a valid
                // json string.
                replaced = replaced.replace("${" + key + "}", value.replace("\"", "\\\""));
            }
            try {
                final ParsedQuery parsed = queryParserService.parse(replaced.getBytes("UTF8"));
                query = parsed.query();
                // TODO: Add filter queries back
                // The solr code claimed that a query that was filtered by
                // analysis, like 'to be' with some chains, will return a null
                // query. Check if that's true here too (probably);
                if (query != null) {
                    esQueryWeight = searcher.createNormalizedWeight(query, true);
                }
            } catch (final Exception e) {
                throw new FeatureException("Unable to parse query feature for " + name, e);
            }
        }

        @Override
        public Explanation explain(LeafReaderContext context, int doc) throws IOException {

            final FeatureScorer r = scorer(context);
            float score = getDefaultValue();
            if (r != null) {
                r.iterator().advance(doc);
                if (r.docID() == doc)
                    score = r.score();
                Explanation subExplain = null;
                if (esQueryWeight != null) {
                    subExplain = esQueryWeight.explain(context, doc);
                }
                return Explanation.match(score, toString(), subExplain);
            } else {
                return Explanation.match(score, "The feature has no value");
            }
        }

        @Override
        public FeatureScorer scorer(LeafReaderContext context) throws IOException {
            Scorer esScorer = null;
            if (esQueryWeight != null) {
                // TODO: Sometimes this scorer comes back null, but i don't
                // understand why. Is
                // it a bug, or an optimization that knows this shard can't have
                // any answers?
                esScorer = esQueryWeight.scorer(context);
            }
            if (esScorer == null) {
                // TODO: is NO_MORE_DOCS correct? or should this be
                // context.reader().maxDoc()?
                // Also what is an appropriate default value? Is 0 reasonable?
                return new ValueFeatureScorer(this, 0f, DocIdSetIterator.all(DocIdSetIterator.NO_MORE_DOCS));
            }

            return new ESQueryFeatureScorer(this, esScorer, esScorer.iterator());
        }

        /**
         * Scorer for a ESQueryFeature
         **/
        public class ESQueryFeatureScorer extends FeatureScorer {
            final private Scorer esScorer;

            public ESQueryFeatureScorer(FeatureWeight weight, Scorer esScorer, DocIdSetIterator itr) {
                super(weight, itr);
                this.esScorer = esScorer;
            }

            @Override
            public float score() throws IOException {
                // Is this try/catch any good? Query parsing failure happens in
                // createWeight
                try {
                    return esScorer.score();
                } catch (UnsupportedOperationException e) {
                    throw new FeatureException(e.toString() + ": " + "Unable to extract feature for " + name, e);
                }
            }
        }
    }

}
