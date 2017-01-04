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

import org.elasticsearch.common.settings.Settings;
import org.junit.Test;
import org.wikimedia.search.ltr.TestRerankBase;
import org.wikimedia.search.ltr.store.LTRStoreService.ModelStoreBuilder;

public class TestFeatureLtrScoringModel extends TestRerankBase {
    @Test
    public void getInstanceTest() throws FeatureException {
        ModelStoreBuilder builder = new ModelStoreBuilder();
        Settings settings = Settings.builder().put("class", ValueFeature.class.getCanonicalName())
            .put("params.value", 42f).build();
        builder.addFeature("fstore", "test", settings);
        final Feature feature = builder.getFeatureStore("fstore").get("test");
        assertNotNull(feature);
        assertEquals("test", feature.getName());
        assertEquals(ValueFeature.class.getCanonicalName(), feature.getClass().getCanonicalName());
    }

    @Test
    public void getInvalidInstanceTest() {
        final String nonExistingClassName = "org.wikimedia.search.ltr.feature.LOLFeature";
        final ClassNotFoundException expectedException = new ClassNotFoundException(nonExistingClassName);
        ModelStoreBuilder builder = new ModelStoreBuilder();
        try {
            builder.addFeature("test", "nonExistingClassName",
                Settings.builder().put("class", nonExistingClassName).build());
            fail("getInvalidInstanceTest failed to throw exception: " + expectedException);
        } catch (Exception actualException) {
            Throwable rootError = getRootCause(actualException);
            assertEquals(expectedException.toString(), rootError.toString());
        }
    }

}
