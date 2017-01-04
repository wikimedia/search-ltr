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
package org.wikimedia.search.ltr.norm;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.elasticsearch.common.settings.Settings;
import org.junit.Test;

public class TestMinMaxNormalizer {

    private Normalizer implTestMinMax(Settings params, float expectedMin, float expectedMax) {
        final Normalizer n = Normalizer.getInstance(MinMaxNormalizer.class.getCanonicalName(), params);
        assertTrue(n instanceof MinMaxNormalizer);
        final MinMaxNormalizer mmn = (MinMaxNormalizer) n;
        assertEquals(mmn.getMin(), expectedMin, 0.0);
        assertEquals(mmn.getMax(), expectedMax, 0.0);
        return n;
    }

    @Test
    public void testInvalidMinMaxNoParams() {
        implTestMinMax(Settings.EMPTY, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY);
    }

    @Test
    public void testInvalidMinMaxMissingMax() {
        Settings params = Settings.builder().put("min", 0f).build();
        implTestMinMax(params, 0.0f, Float.POSITIVE_INFINITY);
    }

    @Test
    public void testInvalidMinMaxMissingMin() {
        Settings params = Settings.builder().put("max", 0f).build();
        implTestMinMax(params, Float.NEGATIVE_INFINITY, 0.0f);
    }

    @Test
    public void testMinMaxNormalizerMinLargerThanMax() {
        final Settings params = Settings.builder().put("min", "10").put("max", "0.0").build();
        implTestMinMax(params, 10.0f, 0.0f);
    }

    @Test
    public void testMinMaxNormalizerMinEqualToMax() {
        final Settings params = Settings.builder().put("min", 10).put("max", 10).build();
        final NormalizerException expectedException = new NormalizerException(
            "MinMax Normalizer delta must not be zero " + "| min = 10.0,max = 10.0,delta = 0.0");
        try {
            implTestMinMax(params, 10.0f, 10.0f);
            fail("testMinMaxNormalizerMinEqualToMax failed to throw exception: " + expectedException);
        } catch (NormalizerException actualException) {
            assertEquals(expectedException.toString(), actualException.toString());
        }
    }

    @Test
    public void testNormalizer() {
        final Settings params = Settings.builder().put("min", 5).put("max", 10).build();
        final Normalizer n = implTestMinMax(params, 5.0f, 10.0f);

        float value = 8;
        assertEquals((value - 5f) / (10f - 5f), n.normalize(value), 0.0001);
        value = 100;
        assertEquals((value - 5f) / (10f - 5f), n.normalize(value), 0.0001);
        value = 150;
        assertEquals((value - 5f) / (10f - 5f), n.normalize(value), 0.0001);
        value = -1;
        assertEquals((value - 5f) / (10f - 5f), n.normalize(value), 0.0001);
        value = 5;
        assertEquals((value - 5f) / (10f - 5f), n.normalize(value), 0.0001);
    }
}
