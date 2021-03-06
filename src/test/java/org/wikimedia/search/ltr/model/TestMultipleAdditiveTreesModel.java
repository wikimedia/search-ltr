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
package org.wikimedia.search.ltr.model;

import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertFirstHit;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertSecondHit;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.assertThirdHit;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.hasId;
import static org.elasticsearch.test.hamcrest.ElasticsearchAssertions.hasScore;
import static org.hamcrest.core.StringContains.containsString;

import org.apache.lucene.search.Explanation;
import org.elasticsearch.action.search.SearchResponse;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.wikimedia.search.ltr.LTRScoringQueryBuilder;

//import static org.junit.internal.matchers.StringContains.containsString;

import org.wikimedia.search.ltr.TestRerankBase;

import com.google.common.collect.ImmutableMap;

public class TestMultipleAdditiveTreesModel extends TestRerankBase {

    @Before
    public void setup() throws Exception {
        setuptest("ltr-mapping.json");

        indexRandom(false, doc("1", "title", "w1", "description", "w1", "popularity", "1"));
        indexRandom(false, doc("2", "title", "w2", "description", "w2", "popularity", "2"));
        indexRandom(false, doc("3", "title", "w3", "description", "w3", "popularity", "3"));
        indexRandom(false, doc("4", "title", "w4", "description", "w4", "popularity", "4"));
        indexRandom(false, doc("5", "title", "w5", "description", "w5", "popularity", "5"));
        refresh();

        // currently needed to force scoring on all docs
        loadFeatures("multipleadditivetreesmodel_features.json");
        loadModels("multipleadditivetreesmodel.json");
    }

    @Test
    public void testMultipleAdditiveTreesScoringWithAndWithoutEfiFeatureMatches() throws Exception {
        // No match scores since user_query not passed in to external feature
        // info and feature depended on it.
        SearchResponse response = queryWithPopularity(new LTRScoringQueryBuilder("multipleadditivetreesmodel")
            .efi(ImmutableMap.<String, String>of("user_query", "dsjkafljjk")));
        assertFirstHit(response, hasScore(-115.0f));
        assertSecondHit(response, hasScore(-116.0f));
        assertThirdHit(response, hasScore(-117.0f));

        // Matched user query since it was passed in
        response = queryWithPopularity(new LTRScoringQueryBuilder("multipleadditivetreesmodel")
            .efi(ImmutableMap.<String, String>of("user_query", "w3")));

        assertFirstHit(response, hasId("3"));
        assertFirstHit(response, hasScore(-17.0f));
        assertSecondHit(response, hasId("5"));
        assertSecondHit(response, hasScore(-115.0f));
        assertThirdHit(response, hasId("4"));
        assertThirdHit(response, hasScore(-116.0f));
    }

    @Ignore
    @Test
    public void multipleAdditiveTreesTestExplain() throws Exception {
        SearchResponse response = client().prepareSearch("test")
            .setQuery(new LTRScoringQueryBuilder("multipleadditivetreesmodel")
                .efi(ImmutableMap.<String, String>of("user_query", "w3")))
            .get();

        // test out the explain feature, make sure it returns something
        Explanation explain = response.getHits().getAt(0).getExplanation();
        assertThat(explain.getDescription(), containsString("multipleadditivetreesmodel"));
        assertThat(explain.getDescription(), containsString(MultipleAdditiveTreesModel.class.getCanonicalName()));

        // This is a bit iffy, the output is heavily structured. But should be
        // deterministic

        // assertThat(qryResult, containsString("-100.0 = tree 0"));
        // assertThat(qryResult, containsString("50.0 = tree 0"));
        // assertThat(qryResult, containsString("-20.0 = tree 1"));
        // assertThat(qryResult, containsString("'matchedTitle':1.0 > 0.5"));
        // assertThat(qryResult, containsString("'matchedTitle':0.0 <= 0.5"));
        //
        // assertThat(qryResult, containsString(" Go Right "));
        // assertThat(qryResult, containsString(" Go Left "));
        // assertThat(qryResult,
        // containsString("'this_feature_doesnt_exist' does not exist in FV"));
    }

    @Test
    public void multipleAdditiveTreesTestNoParams() throws Exception {
        final ModelException expectedException = new ModelException(
            "no trees declared for model multipleadditivetreesmodel_no_params");
        try {
            createModelFromFiles("multipleadditivetreesmodel_no_params.json",
                "multipleadditivetreesmodel_features.json");
            fail("multipleAdditiveTreesTestNoParams failed to throw exception: " + expectedException);
        } catch (Exception actualException) {
            Throwable rootError = getRootCause(actualException);
            assertEquals(expectedException.toString(), rootError.toString());
        }

    }

    @Test
    public void multipleAdditiveTreesTestEmptyParams() throws Exception {
        final ModelException expectedException = new ModelException(
            "no trees declared for model multipleadditivetreesmodel_no_trees");
        try {
            createModelFromFiles("multipleadditivetreesmodel_no_trees.json",
                "multipleadditivetreesmodel_features.json");
            fail("multipleAdditiveTreesTestEmptyParams failed to throw exception: " + expectedException);
        } catch (Exception actualException) {
            Throwable rootError = getRootCause(actualException);
            assertEquals(expectedException.toString(), rootError.toString());
        }
    }

    @Test
    public void multipleAdditiveTreesTestNoWeight() throws Exception {
        final ModelException expectedException = new ModelException(
            "MultipleAdditiveTreesModel tree doesn't contain a weight");
        try {
            createModelFromFiles("multipleadditivetreesmodel_no_weight.json",
                "multipleadditivetreesmodel_features.json");
            fail("multipleAdditiveTreesTestNoWeight failed to throw exception: " + expectedException);
        } catch (Exception actualException) {
            Throwable rootError = getRootCause(actualException);
            assertEquals(expectedException.toString(), rootError.toString());
        }
    }

    @Test
    public void multipleAdditiveTreesTestTreesParamDoesNotContatinTree() throws Exception {
        final ModelException expectedException = new ModelException(
            "MultipleAdditiveTreesModel tree doesn't contain a tree");
        try {
            createModelFromFiles("multipleadditivetreesmodel_no_tree.json", "multipleadditivetreesmodel_features.json");
            fail("multipleAdditiveTreesTestTreesParamDoesNotContatinTree failed to throw exception: "
                + expectedException);
        } catch (Exception actualException) {
            Throwable rootError = getRootCause(actualException);
            assertEquals(expectedException.toString(), rootError.toString());
        }
    }

    @Test
    public void multipleAdditiveTreesTestNoFeaturesSpecified() throws Exception {
        final ModelException expectedException = new ModelException(
            "no features declared for model multipleadditivetreesmodel_no_features");
        try {
            createModelFromFiles("multipleadditivetreesmodel_no_features.json",
                "multipleadditivetreesmodel_features.json");

            fail("multipleAdditiveTreesTestNoFeaturesSpecified failed to throw exception: " + expectedException);
        } catch (ModelException actualException) {
            assertEquals(expectedException.toString(), actualException.toString());
        }
    }

    @Test
    public void multipleAdditiveTreesTestNoRight() throws Exception {
        final ModelException expectedException = new ModelException(
            "MultipleAdditiveTreesModel tree node is missing right");
        try {
            createModelFromFiles("multipleadditivetreesmodel_no_right.json",
                "multipleadditivetreesmodel_features.json");
            fail("multipleAdditiveTreesTestNoRight failed to throw exception: " + expectedException);
        } catch (Exception actualException) {
            Throwable rootError = getRootCause(actualException);
            assertEquals(expectedException.toString(), rootError.toString());
        }
    }

    @Test
    public void multipleAdditiveTreesTestNoLeft() throws Exception {
        final ModelException expectedException = new ModelException(
            "MultipleAdditiveTreesModel tree node is missing left");
        try {
            createModelFromFiles("multipleadditivetreesmodel_no_left.json", "multipleadditivetreesmodel_features.json");
            fail("multipleAdditiveTreesTestNoLeft failed to throw exception: " + expectedException);
        } catch (Exception actualException) {
            Throwable rootError = getRootCause(actualException);
            assertEquals(expectedException.toString(), rootError.toString());
        }
    }

    @Test
    public void multipleAdditiveTreesTestNoThreshold() throws Exception {
        final ModelException expectedException = new ModelException(
            "MultipleAdditiveTreesModel tree node is missing threshold");
        try {
            createModelFromFiles("multipleadditivetreesmodel_no_threshold.json",
                "multipleadditivetreesmodel_features.json");
            fail("multipleAdditiveTreesTestNoThreshold failed to throw exception: " + expectedException);
        } catch (Exception actualException) {
            Throwable rootError = getRootCause(actualException);
            assertEquals(expectedException.toString(), rootError.toString());
        }
    }

    @Test
    public void multipleAdditiveTreesTestMissingTreeFeature() throws Exception {
        final ModelException expectedException = new ModelException(
            "MultipleAdditiveTreesModel tree node is leaf with left=-100.0 and right=75.0");
        try {
            createModelFromFiles("multipleadditivetreesmodel_no_feature.json",
                "multipleadditivetreesmodel_features.json");
            fail("multipleAdditiveTreesTestMissingTreeFeature failed to throw exception: " + expectedException);
        } catch (ModelException actualException) {
            assertEquals(expectedException.toString(), actualException.toString());
        }
    }
}
