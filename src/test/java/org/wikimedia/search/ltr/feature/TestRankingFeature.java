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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.lucene.search.function.FieldValueFactorFunction.Modifier;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.index.query.functionscore.fieldvaluefactor.FieldValueFactorFunctionBuilder;
import org.elasticsearch.index.query.functionscore.script.ScriptScoreFunctionBuilder;
import org.elasticsearch.script.Script;
import org.elasticsearch.script.ScriptService.ScriptType;
import org.junit.Before;
import org.junit.Test;
import org.wikimedia.search.ltr.LTRScoringQueryBuilder;
import org.wikimedia.search.ltr.TestRerankBase;
import org.wikimedia.search.ltr.model.LinearModel;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

public class TestRankingFeature extends TestRerankBase {

    @Before
    public void setup() throws Exception {
        setuptest("ltr-mapping.json");

        List<IndexRequestBuilder> docs = new ArrayList<>();
        docs.add(doc("1", "title", "w1", "description", "w1", "popularity", "1"));
        docs.add(doc("2", "title", "w2 2asd asdd didid", "description", "w2 2asd asdd didid", "popularity", "2"));
        docs.add(doc("3", "title", "w3", "description", "w3", "popularity", "3"));
        docs.add(doc("4", "title", "w4", "description", "w4", "popularity", "4"));
        docs.add(doc("5", "title", "w5", "description", "w5", "popularity", "5"));
        docs.add(doc("6", "title", "w1 w2", "description", "w1 w2", "popularity", "6"));
        docs.add(doc("7", "title", "w1 w2 w3 w4 w5", "description", "w1 w2 w3 w4 w5 w8", "popularity", "7"));
        docs.add(doc("8", "title", "w1 w1 w1 w2 w2 w8", "description", "w1 w1 w1 w2 w2", "popularity", "8"));
        indexRandom(false, docs);
        refresh();
    }

    @Test
    public void testRankingESQueryFeature() throws Exception, Throwable {
        String popularityScript = "pow(doc['popularity'].value, 2)";
        Map<String, Object> popularityQuery = toXContentAsMap(new FunctionScoreQueryBuilder(
            new ScriptScoreFunctionBuilder(new Script(popularityScript, ScriptType.INLINE, "expression", null))));
        Map<String, Object> unpopularityQuery = toXContentAsMap(new FunctionScoreQueryBuilder(
            new FieldValueFactorFunctionBuilder("popularity").modifier(Modifier.RECIPROCAL)));

        loadFeature("powpularityS", ESQueryFeature.class.getCanonicalName(),
            ImmutableMap.<String, Object>of("q", popularityQuery));
        loadFeature("unpopularityS", ESQueryFeature.class.getCanonicalName(),
            ImmutableMap.<String, Object>of("q", unpopularityQuery));

        loadModel("powpularityS-model", LinearModel.class.getCanonicalName(),
            ImmutableList.<Map<String, Object>>of(ImmutableMap.<String, Object>of("name", "powpularityS")),
            ImmutableMap.<String, Object>of("weights", ImmutableMap.<String, Object>of("powpularityS", 1.0f)));
        loadModel("unpopularityS-model", LinearModel.class.getCanonicalName(),
            ImmutableList.<Map<String, Object>>of(ImmutableMap.<String, Object>of("name", "unpopularityS")),
            ImmutableMap.<String, Object>of("weights", ImmutableMap.<String, Object>of("unpopularityS", 1.0f)));

        MatchQueryBuilder matchQueryBuilder = new MatchQueryBuilder("title", "w1");
        SearchResponse response = rescoreQuery(matchQueryBuilder);
        assertFirstHit(response, hasId("1"));
        assertSecondHit(response, hasId("8"));
        assertThirdHit(response, hasId("6"));
        assertFourthHit(response, hasId("7"));

        // Normal term match
        response = rescoreQuery(matchQueryBuilder, new LTRScoringQueryBuilder("powpularityS-model"));
        assertFirstHit(response, hasId("8"));
        assertFirstHit(response, hasScore(64.0f));
        assertSecondHit(response, hasId("7"));
        assertSecondHit(response, hasScore(49.0f));
        assertThirdHit(response, hasId("6"));
        assertThirdHit(response, hasScore(36.0f));
        assertFourthHit(response, hasId("1"));
        assertFourthHit(response, hasScore(1.0f));

        response = rescoreQuery(matchQueryBuilder, new LTRScoringQueryBuilder("unpopularityS-model"));
        assertFirstHit(response, hasId("1"));
        assertFirstHit(response, hasScore(1.0f));
        assertSecondHit(response, hasId("6"));
        assertThirdHit(response, hasId("7"));
        assertFourthHit(response, hasId("8"));

        // bad query ranking feature
        Map<String, Object> badQuery = toXContentAsMap(new FunctionScoreQueryBuilder(
            new FieldValueFactorFunctionBuilder("description").modifier(Modifier.RECIPROCAL)));
        loadFeature("recipdesS", ESQueryFeature.class.getCanonicalName(),
            ImmutableMap.<String, Object>of("q", badQuery));
        loadModel("recipdesS-model", LinearModel.class.getCanonicalName(),
            ImmutableList.<Map<String, Object>>of(ImmutableMap.<String, Object>of("name", "recipdesS")),
            ImmutableMap.<String, Object>of("weights", ImmutableMap.<String, Object>of("recipdesS", 1.0f)));

        try {
            response = rescoreQuery(matchQueryBuilder, new LTRScoringQueryBuilder("recipdesS-model"));
            fail("Expected exception from invalid recipdesS feature");
        } catch (ExecutionException e) {
            assertTrue(e.getMessage().contains("Unable to parse query feature for recipdesS"));
        }
    }

}
