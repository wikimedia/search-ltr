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

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;
import org.wikimedia.search.ltr.TestRerankBase;
import org.wikimedia.search.ltr.store.FeatureStore;
import org.wikimedia.search.ltr.store.LTRStoreService.ModelStoreBuilder;

import com.google.common.collect.ImmutableMap;

public class TestFeatureStore extends TestRerankBase {
    private final static double EPSILON = 0.00001;

    @Test
    public void testDefaultFeatureStoreName() {
        ModelStoreBuilder builder = new ModelStoreBuilder();
        assertEquals("_DEFAULT_", FeatureStore.DEFAULT_FEATURE_STORE_NAME);
        final FeatureStore expectedFeatureStore = builder.getFeatureStore(FeatureStore.DEFAULT_FEATURE_STORE_NAME);
        final FeatureStore actualFeatureStore = builder.getFeatureStore(null);
        assertEquals("getFeatureStore(null) should return the default feature store", expectedFeatureStore,
            actualFeatureStore);
    }

    @Test
    public void testFeatureStoreAdd() throws Exception {
        ModelStoreBuilder builder = new ModelStoreBuilder();
        final FeatureStore fs = builder.getFeatureStore("fstore-testFeature");
        for (int i = 0; i < 5; i++) {
            final String name = "c" + i;

            builder.addFeature("fstore-testFeature", name, mapToSettings(
                createFeatureMap(ValueFeature.class.getCanonicalName(), ImmutableMap.<String, Object>of("value", i))));

            final Feature f = fs.get(name);
            assertNotNull(f);

        }

        assertEquals(5, fs.getFeatures().size());

    }

    @Test
    public void testFeatureStoreGet() throws Exception {
        ModelStoreBuilder builder = new ModelStoreBuilder();
        final FeatureStore fs = builder.getFeatureStore("fstore-testFeature2");
        for (int i = 0; i < 5; i++) {
            final String name = "c" + i;
            builder.addFeature("fstore-testFeature2", name, mapToSettings(
                createFeatureMap(ValueFeature.class.getCanonicalName(), ImmutableMap.<String, Object>of("value", i))));
        }

        for (int i = 0; i < 5; i++) {
            final Feature f = fs.get("c" + i);
            assertEquals("c" + i, f.getName());
            assertTrue(f instanceof ValueFeature);
            final ValueFeature vf = (ValueFeature) f;
            assertEquals(i, vf.getValue(), EPSILON);
        }
    }

    @Test
    public void testMissingFeatureReturnsNull() throws Exception {
        ModelStoreBuilder builder = new ModelStoreBuilder();
        final FeatureStore fs = builder.getFeatureStore("fstore-testFeature3");
        for (int i = 0; i < 5; i++) {
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("value", i);
            final String name = "testc" + (float) i;
            builder.addFeature("fstore-testFeature3", name, mapToSettings(
                createFeatureMap(ValueFeature.class.getCanonicalName(), ImmutableMap.<String, Object>of("value", i))));

        }
        assertNull(fs.get("missing_feature_name"));
    }

}
