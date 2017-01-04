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
import org.elasticsearch.index.query.IdsQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.junit.Before;
import org.junit.Test;
import org.wikimedia.search.ltr.LTRScoringQueryBuilder;
import org.wikimedia.search.ltr.TestRerankBase;
import org.wikimedia.search.ltr.model.LinearModel;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class TestFieldValueFeature extends TestRerankBase {

    private static final float FIELD_VALUE_FEATURE_DEFAULT_VAL = 0.0f;

    @Before
    public void setup() throws Exception {
        setuptest("ltr-mapping.json");

        indexRandom(false, doc("1", "title", "w1", "description", "w1", "popularity", "1"));
        indexRandom(false,
            doc("2", "title", "w2 2asd asdd didid", "description", "w2 2asd asdd didid", "popularity", "2"));
        indexRandom(false, doc("3", "title", "w3", "description", "w3", "popularity", "3"));
        indexRandom(false, doc("4", "title", "w4", "description", "w4", "popularity", "4"));
        indexRandom(false, doc("5", "title", "w5", "description", "w5", "popularity", "5"));
        indexRandom(false, doc("6", "title", "w1 w2", "description", "w1 w2", "popularity", "6"));
        indexRandom(false, doc("7", "title", "w1 w2 w3 w4 w5", "description", "w1 w2 w3 w4 w5 w8", "popularity", "7"));
        indexRandom(false, doc("8", "title", "w1 w1 w1 w2 w2 w8", "description", "w1 w1 w1 w2 w2", "popularity", "8"));
        // a document without the popularity field
        indexRandom(false, doc("42", "title", "NO popularity", "description", "NO popularity"));
        refresh();

        loadFeature("popularity", FieldValueFeature.class.getCanonicalName(),
            ImmutableMap.<String, Object>of("field", "popularity"));
        loadModel("popularity-model", LinearModel.class.getCanonicalName(),
            ImmutableList.<Map<String, Object>>of(ImmutableMap.<String, Object>of("name", "popularity")),
            ImmutableMap.<String, Object>of("weights", ImmutableMap.<String, Object>of("popularity", 1.0f)));
    }

    @Test
    public void testRanking() throws Exception {
        //// Normal term match
        SearchResponse response = rescoreQuery(1f, new MatchQueryBuilder("title", "w1"), null);
        assertFirstHit(response, hasId("1"));
        assertSecondHit(response, hasId("8"));
        assertThirdHit(response, hasId("6"));
        assertFourthHit(response, hasId("7"));

        response = rescoreQuery(new MatchQueryBuilder("title", "w1"), new LTRScoringQueryBuilder("popularity-model"));
        assertFirstHit(response, hasId("8"));
        assertFirstHit(response, hasScore(8f));
        assertSecondHit(response, hasId("7"));
        assertSecondHit(response, hasScore(7f));
        assertThirdHit(response, hasId("6"));
        assertThirdHit(response, hasScore(6f));
        assertFourthHit(response, hasId("1"));

        response = rescoreQuery(new LTRScoringQueryBuilder("popularity-model"));
        assertFirstHit(response, hasId("8"));
        assertSecondHit(response, hasId("7"));
        assertThirdHit(response, hasId("6"));
        assertFourthHit(response, hasId("5"));
        assertFourthHit(response, hasScore(5f));
    }

    @Test
    public void testIfADocumentDoesntHaveAFieldDefaultValueIsReturned() throws Exception {
        IdsQueryBuilder idsQueryBuilder = new IdsQueryBuilder("test").addIds("42");
        SearchResponse response = rescoreQuery(1.0f, idsQueryBuilder, null);
        assertFirstHit(response, hasId("42"));
        assertFirstHit(response, hasScore(1f));

        response = rescoreQuery(idsQueryBuilder, new LTRScoringQueryBuilder("popularity-model"));
        assertFirstHit(response, hasId("42"));
        assertFirstHit(response, hasScore(FIELD_VALUE_FEATURE_DEFAULT_VAL));
    }

    @Test
    public void testIfADocumentDoesntHaveAFieldASetDefaultValueIsReturned() throws Exception {
        loadFeature("popularity42", FieldValueFeature.class.getCanonicalName(),
            ImmutableMap.<String, Object>of("field", "popularity", "defaultValue", 42f));
        loadModel("popularity-model42", LinearModel.class.getCanonicalName(),
            ImmutableList.<Map<String, Object>>of(ImmutableMap.<String, Object>of("name", "popularity42")),
            ImmutableMap.<String, Object>of("weights", ImmutableMap.<String, Object>of("popularity42", 1f)));

        IdsQueryBuilder idsQueryBuilder = new IdsQueryBuilder("test").addIds("42");
        SearchResponse response = rescoreQuery(1f, idsQueryBuilder, null);
        assertFirstHit(response, hasId("42"));
        assertFirstHit(response, hasScore(1f));

        response = rescoreQuery(idsQueryBuilder, new LTRScoringQueryBuilder("popularity-model42"));
        assertFirstHit(response, hasId("42"));
        assertFirstHit(response, hasScore(42f));
    }

    @Test
    public void testThatIfaFieldDoesNotExistDefaultValueIsReturned() throws Exception {
        // using a different fstore to avoid a clash with the other tests
        loadFeature("not-existing-field", FieldValueFeature.class.getCanonicalName(),
            ImmutableMap.<String, Object>of("field", "cowabunga"));
        loadModel("not-existing-field-model", LinearModel.class.getCanonicalName(),
            ImmutableList.<Map<String, Object>>of(ImmutableMap.<String, Object>of("name", "not-existing-field")),
            ImmutableMap.<String, Object>of("weights", ImmutableMap.<String, Object>of("not-existing-field", 1f)));

        IdsQueryBuilder idsQueryBuilder = new IdsQueryBuilder("test").addIds("42");
        SearchResponse response = rescoreQuery(idsQueryBuilder, new LTRScoringQueryBuilder("not-existing-field-model"));
        assertFirstHit(response, hasScore(FIELD_VALUE_FEATURE_DEFAULT_VAL));
    }

    public void testThatFieldValueLoadsFromSource() throws Exception {
        loadFeature("popularity2", FieldValueFeature.class.getCanonicalName(),
            ImmutableMap.<String, Object>of("field", "popularity2", "source", true));
        loadModel("popularity2-model", LinearModel.class.getCanonicalName(),
            ImmutableList.<Map<String, Object>>of(ImmutableMap.<String, Object>of("name", "popularity2")),
            ImmutableMap.<String, Object>of("weights", ImmutableMap.<String, Object>of("popularity2", 1f)));

        indexRandom(false, doc("77", "popularity2", 33));
        refresh();

        IdsQueryBuilder idsQueryBuilder = new IdsQueryBuilder("test").addIds("77");
        SearchResponse response = rescoreQuery(idsQueryBuilder, new LTRScoringQueryBuilder("popularity2-model"));
        assertFirstHit(response, hasScore(33f));
    }
}
