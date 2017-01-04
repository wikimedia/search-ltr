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
package org.wikimedia.search.ltr;

import java.io.IOException;

import org.elasticsearch.common.logging.ESLogger;
import org.elasticsearch.common.logging.ESLoggerFactory;
import org.elasticsearch.common.xcontent.XContent;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.mapper.Uid;
import org.wikimedia.search.ltr.LTRScoringQuery.FeatureInfo;

/**
 * FeatureLogger can be registered in a model and provide a strategy for logging
 * the feature values.
 */
public abstract class FeatureLogger {

    private static final ESLogger log = ESLoggerFactory.getLogger(FeatureLogger.class.getName());

    protected enum FeatureFormat {
        DENSE, SPARSE
    };

    protected final FeatureFormat featureFormat;
    protected final String marker;

    protected FeatureLogger(FeatureFormat f, String marker) {
        this.featureFormat = f;
        this.marker = marker;
    }

    /**
     * Log will be called every time that the model generates the feature values
     * for a document and a query.
     *
     * @param uid
     *            ES document id whose features we are saving
     * @param featuresInfo
     *            List of all the FeatureInfo objects which contain name and
     *            value for all the features triggered by the result set
     * @return true if the logger successfully logged the features, false
     *         otherwise.
     */

    public boolean log(Uid uid, FeatureInfo[] featuresInfo) throws IOException {
        if (uid == null) {
            log.info("null uid");
            return false;
        }
        final String featureVector = makeFeatureVector(uid, featuresInfo);
        if (featureVector == null) {
            log.info("null feature vector");

            return false;
        }

        log.info(featureVector);
        return true;
    }

    /**
     * returns a FeatureLogger that logs the features in output, using the
     * format specified in the 'stringFormat' param: 'csv' will log the features
     * as a unique string in csv format 'json' will log the features in a map in
     * a Map of featureName keys to featureValue values if format is null or
     * empty, csv format will be selected. 'featureFormat' param: 'dense' will
     * write features in dense format, 'sparse' will write the features in
     * sparse format, null or empty will default to 'sparse'
     *
     *
     * @return a feature logger for the format specified.
     */
    public static FeatureLogger createFeatureLogger(String stringFormat, String featureFormat, String marker) {
        final FeatureFormat f;
        if (featureFormat == null || featureFormat.isEmpty() || featureFormat.equals("sparse")) {
            f = FeatureFormat.SPARSE;
        } else if (featureFormat.equals("dense")) {
            f = FeatureFormat.DENSE;
        } else {
            f = FeatureFormat.SPARSE;
            log.warn("unknown feature logger feature format {} | {}", stringFormat, featureFormat);
        }
        if ((stringFormat == null) || stringFormat.isEmpty()) {
            return new CSVFeatureLogger(f, marker);
        }
        if (stringFormat.equals("csv")) {
            return new CSVFeatureLogger(f, marker);
        }
        if (stringFormat.equals("json")) {
            return new MapFeatureLogger(f, marker);
        }
        log.warn("unknown feature logger string format {} | {}", stringFormat, featureFormat);
        return null;

    }

    public abstract String makeFeatureVector(Uid uid, FeatureInfo[] featuresInfo) throws IOException;

    public static class MapFeatureLogger extends FeatureLogger {
        XContent xContent = JsonXContent.jsonXContent;

        public MapFeatureLogger(FeatureFormat f, String marker) {
            super(f, marker);
        }

        @Override
        public String makeFeatureVector(Uid uid, FeatureInfo[] featuresInfo) throws IOException {
            if (featuresInfo.length == 0) {
                return null;
            }
            boolean isDense = featureFormat.equals(FeatureFormat.DENSE);
            XContentBuilder builder = XContentBuilder.builder(xContent);
            builder.startObject();
            builder.field("_id", uid.id());
            builder.field("_type", uid.type());
            if (marker != null) {
                builder.field("_marker", marker);
            }
            builder.startObject("vec");
            for (FeatureInfo featInfo : featuresInfo) {
                if (featInfo.isUsed() || isDense) {
                    builder.field(featInfo.getName(), featInfo.getValue());
                }
            }
            builder.endObject();
            builder.endObject();

            return builder.string();
        }
    }

    public static class CSVFeatureLogger extends FeatureLogger {
        StringBuilder sb = new StringBuilder(500);
        char keyValueSep = ':';
        char featureSep = ';';

        public CSVFeatureLogger(FeatureFormat f, String marker) {
            super(f, marker);
        }

        public CSVFeatureLogger setKeyValueSep(char keyValueSep) {
            this.keyValueSep = keyValueSep;
            return this;
        }

        public CSVFeatureLogger setFeatureSep(char featureSep) {
            this.featureSep = featureSep;
            return this;
        }

        @Override
        public String makeFeatureVector(Uid uid, FeatureInfo[] featuresInfo) {
            if (featuresInfo.length == 0) {
                return null;
            }
            boolean isDense = featureFormat.equals(FeatureFormat.DENSE);
            sb.append("_id").append(keyValueSep).append(uid.id()).append(featureSep).append("_type").append(keyValueSep)
                .append(uid.type()).append(featureSep);
            if (marker != null) {
                sb.append("_marker").append(keyValueSep).append(marker).append(featureSep);
            }
            for (FeatureInfo featInfo : featuresInfo) {
                if (featInfo.isUsed() || isDense) {
                    sb.append(featInfo.getName()).append(keyValueSep).append(featInfo.getValue());
                    sb.append(featureSep);
                }
            }

            final String features = sb.substring(0, sb.length() - 1);
            sb.setLength(0);

            return features;
        }
    }
}
