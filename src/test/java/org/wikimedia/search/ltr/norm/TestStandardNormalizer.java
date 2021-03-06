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

public class TestStandardNormalizer {
    private Normalizer implTestStandard(Settings params, float expectedAvg, float expectedStd) {
        final Normalizer n = Normalizer.getInstance(StandardNormalizer.class.getCanonicalName(), params);
        assertTrue(n instanceof StandardNormalizer);
        final StandardNormalizer sn = (StandardNormalizer) n;
        assertEquals(sn.getAvg(), expectedAvg, 0.0);
        assertEquals(sn.getStd(), expectedStd, 0.0);
        return n;
    }

    @Test
    public void testNormalizerNoParams() {
        implTestStandard(Settings.EMPTY, 0.0f, 1.0f);
    }

    @Test
    public void testInvalidSTD() {
        final Settings params = Settings.builder().put("std", 0f).build();
        final NormalizerException expectedException = new NormalizerException(
            "Standard Normalizer standard deviation must be positive " + "| avg = 0.0,std = 0.0");
        try {
            implTestStandard(params, 0.0f, 0.0f);
            fail("testInvalidSTD failed to throw exception: " + expectedException);
        } catch (NormalizerException actualException) {
            assertEquals(expectedException.toString(), actualException.toString());
        }
    }

    @Test
    public void testInvalidSTD2() {
        final Settings params = Settings.builder().put("std", -1).build();
        final NormalizerException expectedException = new NormalizerException(
            "Standard Normalizer standard deviation must be positive " + "| avg = 0.0,std = -1.0");
        try {
            implTestStandard(params, 0.0f, -1f);
            fail("testInvalidSTD2 failed to throw exception: " + expectedException);
        } catch (NormalizerException actualException) {
            assertEquals(expectedException.toString(), actualException.toString());
        }
    }

    @Test
    public void testInvalidSTD3() {
        final Settings params = Settings.builder().put("avg", 1f).put("std", 0f).build();
        final NormalizerException expectedException = new NormalizerException(
            "Standard Normalizer standard deviation must be positive " + "| avg = 1.0,std = 0.0");
        try {
            implTestStandard(params, 1f, 0f);
            fail("testInvalidSTD3 failed to throw exception: " + expectedException);
        } catch (NormalizerException actualException) {
            assertEquals(expectedException.toString(), actualException.toString());
        }
    }

    @Test
    public void testNormalizer() {
        Settings params = Settings.builder().put("avg", 0f).put("std", 1f).build();
        final Normalizer identity = implTestStandard(params, 0f, 1f);

        float value = 8;
        assertEquals(value, identity.normalize(value), 0.0001);
        value = 150;
        assertEquals(value, identity.normalize(value), 0.0001);
        params = Settings.builder().put("avg", 10f).put("std", 1.5f).build();
        final Normalizer norm = Normalizer.getInstance(StandardNormalizer.class.getCanonicalName(), params);

        for (final float v : new float[] { 10f, 20f, 25f, 30f, 31f, 40f, 42f, 100f, 10000000f }) {
            assertEquals((v - 10f) / (1.5f), norm.normalize(v), 0.0001);
        }
    }
}
