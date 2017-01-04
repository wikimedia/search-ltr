package org.wikimedia.search.ltr.action.feature.put;

import static org.elasticsearch.action.ValidateActions.addValidationError;
import static org.elasticsearch.common.settings.Settings.readSettingsFromStream;
import static org.elasticsearch.common.settings.Settings.writeSettingsToStream;
import static org.elasticsearch.common.settings.Settings.Builder.EMPTY_SETTINGS;

import java.io.IOException;
import java.util.Map;

import org.elasticsearch.ElasticsearchGenerationException;
import org.elasticsearch.action.ActionRequestValidationException;
import org.elasticsearch.action.support.master.AcknowledgedRequest;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.common.xcontent.XContentType;

public class PutFeaturesRequest extends AcknowledgedRequest<PutFeaturesRequest> {
    private Settings features = EMPTY_SETTINGS;

    public PutFeaturesRequest() {
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException validationException = null;
        if (features.names().size() == 0) {
            validationException = addValidationError("no features provided", validationException);
        }
        return validationException;

    }

    public Settings features() {
        return features;
    }

    public PutFeaturesRequest features(Settings features) {
        features(Settings.builder().put(features));
        return this;
    }

    public PutFeaturesRequest features(Settings.Builder features) {
        this.features = features.normalizePrefix("features.").build();
        return this;
    }

    public PutFeaturesRequest features(String source) {
        features(Settings.builder().loadFromSource(source));
        return this;
    }

    public PutFeaturesRequest features(Map<String, Map<String, Map<String, Object>>> features) {
        try {
            XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
            builder.startObject();
            builder.startObject("features");
            for (Map.Entry<String, Map<String, Map<String, Object>>> outerEntry : features.entrySet()) {
                builder.startObject(outerEntry.getKey());
                for (Map.Entry<String, Map<String, Object>> innerEntry : outerEntry.getValue().entrySet()) {
                    builder.field(innerEntry.getKey());
                    builder.map(innerEntry.getValue());
                }
                builder.endObject();
            }
            builder.endObject();
            builder.endObject();
            features(builder.string());
        } catch (IOException e) {
            throw new ElasticsearchGenerationException("Failed to generate [" + features + "]", e);
        }
        return this;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        features = readSettingsFromStream(in);
        readTimeout(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        writeSettingsToStream(features, out);
        writeTimeout(out);
    }
}
