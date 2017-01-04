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

import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertFirstHit;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.hasId;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.hasScore;

import org.elasticsearch.action.search.SearchResponse;
import org.junit.Before;
import org.junit.Test;
import org.wikimedia.search.ltr.LTRScoringQueryBuilder;
import org.wikimedia.search.ltr.TestRerankBase;

import com.google.common.collect.ImmutableMap;

public class TestExternalValueFeatures extends TestRerankBase {

    @Before
    public void setup() throws Exception {
        setuptest("ltr-mapping.json");

        indexRandom(false, doc("1", "title", "w1", "description", "w1", "popularity", "1"));
        indexRandom(false, doc("2", "title", "w2", "description", "w2", "popularity", "2"));
        indexRandom(false, doc("3", "title", "w3", "description", "w3", "popularity", "3"));
        indexRandom(false, doc("4", "title", "w4", "description", "w4", "popularity", "4"));
        indexRandom(false, doc("5", "title", "w5", "description", "w5", "popularity", "5"));
        refresh();

        loadFeatures("external_features_for_sparse_processing.json");
        loadModels("multipleadditivetreesmodel_external_binary_features.json");
    }

    @Test
    public void efiFeatureProcessing_oneEfiMissing_shouldNotCalculateMissingFeature() throws Exception {
        SearchResponse response = queryWithPopularity(new LTRScoringQueryBuilder("external_model_binary_feature")
            .efi(ImmutableMap.<String, String>of("user_device_tablet", "1")).marker("foo"));

        assertFirstHit(response, hasId("5"));
        assertFirstHit(response, hasScore(70.0f));
    }

    @Test
    public void efiFeatureProcessing_allEfisMissing_shouldReturnZeroScore() throws Exception {
        SearchResponse response = queryWithPopularity(new LTRScoringQueryBuilder("external_model_binary_feature"));

        assertFirstHit(response, hasId("5"));
        assertFirstHit(response, hasScore(5.0f));
    }

}
