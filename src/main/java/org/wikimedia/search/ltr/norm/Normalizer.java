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

import java.lang.Class;
import java.lang.ClassNotFoundException;
import java.lang.NoSuchMethodException;
import java.lang.Exception;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.lucene.search.Explanation;
import org.elasticsearch.common.settings.Settings;

/**
 * A normalizer normalizes the value of a feature. After the feature values have
 * been computed, the {@link Normalizer#normalize(float)} methods will be called
 * and the resulting values will be used by the model.
 */
public abstract class Normalizer {

    public abstract float normalize(float value);

    public abstract LinkedHashMap<String, Object> paramsToMap();

    public Explanation explain(Explanation explain) {
        final float normalized = normalize(explain.getValue());
        final String explainDesc = "normalized using " + toString();

        return Explanation.match(normalized, explainDesc, explain);
    }

    public static Normalizer getInstance(String className, Settings params) {
        Class<?> clazz;
        try {
            clazz = Normalizer.class.getClassLoader().loadClass(className);
        } catch (ClassNotFoundException e) {
            throw new NormalizerException("Normalizer type does not exist: " + className, e);
        }
        if (!Normalizer.class.isAssignableFrom(clazz)) {
            throw new NormalizerException("Normalizer type is not a Normalizer: " + className);
        }
        Normalizer f;
        try {
            f = (Normalizer) clazz.getConstructor(Settings.class).newInstance(params);
        } catch (NoSuchMethodException e) {
            throw new NormalizerException("Normalizer type does not have valid constructor: " + className, e);
        } catch (Exception e) {
            throw new NormalizerException("Normalizer type failed construction: " + className, e);
        }
        f.validate();
        return f;
    }

    /**
     * On construction of a normalizer, this function confirms that the
     * normalizer parameters are validated
     * 
     * @throws NormalizerException
     *             Normalizer Exception
     */
    protected void validate() throws NormalizerException {

    }

}
