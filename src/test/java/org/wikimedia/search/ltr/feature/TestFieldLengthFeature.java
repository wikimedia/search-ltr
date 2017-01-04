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
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertFourthHit;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertSecondHit;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertThirdHit;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.hasId;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.hasScore;

import java.util.Map;

import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.junit.Before;
import org.junit.Test;
import org.wikimedia.search.ltr.LTRScoringQueryBuilder;
import org.wikimedia.search.ltr.TestRerankBase;
import org.wikimedia.search.ltr.model.LinearModel;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class TestFieldLengthFeature extends TestRerankBase {
    private static ESLogger log = ESLoggerFactory.getLogger(TestFieldLengthFeature.class.getCanonicalName());

    @Before
    public void setup() throws Exception {
        setuptest("ltr-mapping.json");

        indexRandom(false, doc("1", "title", "w1", "description", "w1"));
        indexRandom(false, doc("2", "title", "w2 2asd asdd didid", "description", "w2 2asd asdd didid"));
        indexRandom(false, doc("3", "title", "w3", "description", "w3"));
        indexRandom(false, doc("4", "title", "w4", "description", "w4"));
        indexRandom(false, doc("5", "title", "w5", "description", "w5"));
        indexRandom(false, doc("6", "title", "w1 w2", "description", "w1 w2"));
        indexRandom(false, doc("7", "title", "w1 w2 w3 w4 w5", "description", "w1 w2 w3 w4 w5 w8"));
        indexRandom(false, doc("8", "title", "w1 w1 w1 w2 w2 w8", "description", "w1 w1 w1 w2 w2"));
        refresh();
    }

    private SearchResponse queryWithTitle(String title, LTRScoringQueryBuilder rescoreQueryBuilder) throws Exception {
        return rescoreQuery(new MatchQueryBuilder("title", title), rescoreQueryBuilder);
    }

    @Test
    public void testIfFieldIsMissingInDocumentLengthIsZero() throws Exception {
        // add a document without the field 'description'
        indexRandom(false, doc("42", "title", "w10"));
        refresh();

        loadFeature("description-length2", FieldLengthFeature.class.getCanonicalName(),
            ImmutableMap.<String, Object>of("field", "description"));
        loadModel("description-model2", LinearModel.class.getCanonicalName(),
            ImmutableList.<Map<String, Object>>of(ImmutableMap.<String, Object>of("name", "description-length2")),
            ImmutableMap.<String, Object>of("weights", ImmutableMap.<String, Object>of("description-length2", 1.0f)));

        SearchResponse response = queryWithTitle("w10", new LTRScoringQueryBuilder("description-model2"));
        assertFirstHit(response, hasId("42"));
        assertFirstHit(response, hasScore(0.0f));
    }

    @Test
    public void testIfFieldIsEmptyLengthIsZero() throws Exception {
        // add a document without the field 'description'
        indexRandom(false, doc("43", "title", "w11", "description", ""));
        refresh();

        loadFeature("description-length3", FieldLengthFeature.class.getCanonicalName(),
            ImmutableMap.<String, Object>of("field", "description"));
        loadModel("description-model3", LinearModel.class.getCanonicalName(),
            ImmutableList.<Map<String, Object>>of(ImmutableMap.<String, Object>of("name", "description-length3")),
            ImmutableMap.<String, Object>of("weights", ImmutableMap.<String, Object>of("description-length3", 1.0f)));

        SearchResponse response = queryWithTitle("w11", new LTRScoringQueryBuilder("description-model3"));
        assertFirstHit(response, hasId("43"));
        assertFirstHit(response, hasScore(0.0f));
    }

    @Test
    public void testRanking() throws Exception {
        loadFeature("title-length", FieldLengthFeature.class.getCanonicalName(),
            ImmutableMap.<String, Object>of("field", "title"));
        loadModel("title-model", LinearModel.class.getCanonicalName(),
            ImmutableList.<Map<String, Object>>of(ImmutableMap.<String, Object>of("name", "title-length")),
            ImmutableMap.<String, Object>of("weights", ImmutableMap.<String, Object>of("title-length", 1.0f)));
        loadFeature("description-length", FieldLengthFeature.class.getCanonicalName(),
            ImmutableMap.<String, Object>of("field", "description"));
        loadModel("description-model", LinearModel.class.getCanonicalName(),
            ImmutableList.<Map<String, Object>>of(ImmutableMap.<String, Object>of("name", "description-length")),
            ImmutableMap.<String, Object>of("weights", ImmutableMap.<String, Object>of("description-length", 1.0f)));

        // Normal term match
        SearchResponse response = queryWithTitle("w1", new LTRScoringQueryBuilder("title-model"));
        assertFirstHit(response, hasId("8"));
        assertSecondHit(response, hasId("7"));
        assertThirdHit(response, hasId("6"));
        assertFourthHit(response, hasId("1"));

        // Straight rescore, without term match
        response = rescoreQuery(new LTRScoringQueryBuilder("title-model"));
        assertFirstHit(response, hasId("8"));
        assertSecondHit(response, hasId("7"));
        assertThirdHit(response, hasId("2"));
        assertFourthHit(response, hasId("6"));

        // Rescore against description length
        response = queryWithTitle("w1", new LTRScoringQueryBuilder("description-model"));
        assertFirstHit(response, hasId("7"));
        assertSecondHit(response, hasId("8"));
        assertThirdHit(response, hasId("6"));
        assertFourthHit(response, hasId("1"));
    }

}
