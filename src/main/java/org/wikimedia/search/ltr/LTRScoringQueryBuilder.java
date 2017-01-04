package org.wikimedia.search.ltr;

import java.io.IOException;
import java.util.Map;

import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.QueryBuilder;

public class LTRScoringQueryBuilder extends QueryBuilder {
    private String modelName = null;
    private boolean extractAllFeatures = false;
    private String marker = null;
    private String featureFormat = null;
    private String stringFormat = null;
    private Map<String, String> efi;

    public LTRScoringQueryBuilder(String modelName) {
        this.modelName = modelName;
    }

    public LTRScoringQueryBuilder extractAllFeatures(boolean extractAllFeatures) {
        this.extractAllFeatures = extractAllFeatures;
        return this;
    }

    public LTRScoringQueryBuilder marker(String marker) {
        this.marker = marker;
        return this;
    }

    public LTRScoringQueryBuilder featureFormat(String format) {
        featureFormat = format;
        return this;
    }

    public LTRScoringQueryBuilder stringFormat(String format) {
        stringFormat = format;
        return this;
    }

    public LTRScoringQueryBuilder efi(Map<String, String> efi) {
        this.efi = efi;
        return this;
    }

    @Override
    protected void doXContent(XContentBuilder builder, Params params) throws IOException {
        builder.startObject(LTRScoringQueryParser.NAME);
        builder.field("model", modelName);
        if (extractAllFeatures != false) {
            builder.field("extractAllFeatures", extractAllFeatures);
        }
        if (efi != null && !efi.isEmpty()) {
            builder.field("efi");
            builder.map(efi);
        }
        if (marker != null || featureFormat != null || stringFormat != null) {
            builder.startObject("logger");
            if (marker != null) {
                builder.field("marker", marker);
            }
            if (featureFormat != null) {
                builder.field("featureFormat", featureFormat);
            }
            if (stringFormat != null) {
                builder.field("stringFormat", stringFormat);
            }
            builder.endObject();
        }
        builder.endObject();
    }
}
