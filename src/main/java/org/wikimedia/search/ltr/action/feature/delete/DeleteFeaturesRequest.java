package org.wikimedia.search.ltr.action.feature.delete;

import static org.elasticsearch.action.ValidateActions.addValidationError;
import static org.elasticsearch.common.settings.Settings.readSettingsFromStream;
import static org.elasticsearch.common.settings.Settings.writeSettingsToStream;
import static org.elasticsearch.common.settings.Settings.Builder.EMPTY_SETTINGS;

import java.io.IOException;
import java.util.List;
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

public class DeleteFeaturesRequest extends AcknowledgedRequest<DeleteFeaturesRequest> {
    private Settings features = EMPTY_SETTINGS;

    public DeleteFeaturesRequest() {
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

    public DeleteFeaturesRequest features(Settings features) {
        features(Settings.builder().put(features));
        return this;
    }

    public DeleteFeaturesRequest features(Settings.Builder features) {
        this.features = features.normalizePrefix("features.").build();
        return this;
    }

    public DeleteFeaturesRequest features(String source) {
        features(Settings.settingsBuilder().loadFromSource(source));
        return this;
    }

    public DeleteFeaturesRequest features(Map<String, List<String>> features) {
        try {
            XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
            builder.startObject();
            builder.startObject("features");
            for (Map.Entry<String, List<String>> entry : features.entrySet()) {
                builder.startArray(entry.getKey());
                for (String feature : entry.getValue()) {
                    builder.value(feature);
                }
                builder.endArray();
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
