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

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.shard.MergeSchedulerConfig;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.junit.Before;
import org.junit.Test;
import org.wikimedia.search.ltr.LTRScoringQueryBuilder;
import org.wikimedia.search.ltr.TestRerankBase;

import com.google.common.collect.ImmutableMap;

public class TestFeatureExtractionFromMultipleSegments extends TestRerankBase {
    static final String AB = "abcdefghijklmnopqrstuvwxyz";

    static String randomString(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(AB.charAt(random().nextInt(AB.length())));
        }
        return sb.toString();
    }

    @Override
    protected Settings nodeSettings(int nodeOrdinal) {
        return Settings.builder().put(super.nodeSettings(nodeOrdinal))
            // Not sure we can really stop segment merging to make this work.
            // Validation caps this to only 1. io limits are only considered
            // for "big" (>50MB) merges. Do our best i guess.
            .put(MergeSchedulerConfig.MAX_THREAD_COUNT, 1).put(MergeSchedulerConfig.MAX_MERGE_COUNT, 1).build();
    }

    @Before
    public void setup() throws Exception {
        setuptest("ltr-mapping.json");

        loadFeatures("comp_features.json");
        loadModels("linear_model-comp_features.json");

        // index 400 documents
        for (int i = 0; i < 400; i = i + 20) {
            indexRandom(false, doc(new Integer(i).toString(), "popularity", "201", "description",
                "apple is a company " + randomString(i % 6 + 3), "normHits", "0.1"));
            indexRandom(false, doc(new Integer(i + 1).toString(), "popularity", "201", "description",
                "d " + randomString(i % 6 + 3), "normHits", "0.11"));

            indexRandom(false, doc(new Integer(i + 2).toString(), "popularity", "201", "description",
                "apple is a company too " + randomString(i % 6 + 3), "normHits", "0.1"));
            indexRandom(false, doc(new Integer(i + 3).toString(), "popularity", "201", "description",
                "new york city is big apple " + randomString(i % 6 + 3), "normHits", "0.11"));

            indexRandom(false, doc(new Integer(i + 6).toString(), "popularity", "301", "description",
                "function name " + randomString(i % 6 + 3), "normHits", "0.1"));
            indexRandom(false, doc(new Integer(i + 7).toString(), "popularity", "301", "description",
                "function " + randomString(i % 6 + 3), "normHits", "0.1"));

            indexRandom(false, doc(new Integer(i + 8).toString(), "popularity", "301", "description",
                "This is a sample function for testing " + randomString(i % 6 + 3), "normHits", "0.1"));
            indexRandom(false, doc(new Integer(i + 9).toString(), "popularity", "301", "description",
                "Function to check out stock prices " + randomString(i % 6 + 3), "normHits", "0.1"));
            indexRandom(false, doc(new Integer(i + 10).toString(), "popularity", "301", "description",
                "Some descriptions " + randomString(i % 6 + 3), "normHits", "0.1"));

            indexRandom(false, doc(new Integer(i + 11).toString(), "popularity", "201", "description",
                "apple apple is a company " + randomString(i % 6 + 3), "normHits", "0.1"));
            indexRandom(false, doc(new Integer(i + 12).toString(), "popularity", "201", "description",
                "Big Apple is New York.", "normHits", "0.01"));
            indexRandom(false, doc(new Integer(i + 13).toString(), "popularity", "201", "description",
                "New some York is Big. " + randomString(i % 6 + 3), "normHits", "0.1"));

            indexRandom(false, doc(new Integer(i + 14).toString(), "popularity", "201", "description",
                "apple apple is a company " + randomString(i % 6 + 3), "normHits", "0.1"));
            indexRandom(false, doc(new Integer(i + 15).toString(), "popularity", "201", "description",
                "Big Apple is New York.", "normHits", "0.01"));
            indexRandom(false,
                doc(new Integer(i + 16).toString(), "popularity", "401", "description", "barack h", "normHits", "0.0"));
            indexRandom(false, doc(new Integer(i + 17).toString(), "popularity", "201", "description",
                "red delicious apple " + randomString(i % 6 + 3), "normHits", "0.1"));
            indexRandom(false, doc(new Integer(i + 18).toString(), "popularity", "201", "description",
                "nyc " + randomString(i % 6 + 3), "normHits", "0.11"));
        }
        refresh();
    }

    @Test
    public void testFeatureExtractionFromMultipleSegments() throws Exception {
        SearchSourceBuilder builder = rescoreQueryBuilder(new MatchQueryBuilder("description", "apple"),
            new LTRScoringQueryBuilder("linear").efi(ImmutableMap.<String, String>of("user_query", "apple")));
        int numRows = 100;
        builder.size(numRows);
        // request 100 rows, if any rows are fetched from the second or
        // subsequent segments the tests should succeed if
        // LTRRescorer::extractFeaturesInfo() advances the doc iterator properly
        SearchResponse response = doSearch(builder);

        int passCount = 0;
        for (SearchHit hit : response.getHits()) {
            assert (hit.score() > 0);
            // assert (features.length() > 0);
            ++passCount;
        }
        assertEquals(passCount, numRows);
    }
}
