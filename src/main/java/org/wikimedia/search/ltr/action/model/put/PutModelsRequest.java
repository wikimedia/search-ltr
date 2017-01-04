package org.wikimedia.search.ltr.action.model.put;

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

public class PutModelsRequest extends AcknowledgedRequest<PutModelsRequest> {
    private Settings models = EMPTY_SETTINGS;

    public PutModelsRequest() {
    }

    @Override
    public ActionRequestValidationException validate() {
        ActionRequestValidationException validationException = null;
        if (models.names().size() == 0) {
            validationException = addValidationError("no models provided", validationException);
        }
        return validationException;
    }

    public Settings models() {
        return models;
    }

    public PutModelsRequest models(Settings models) {
        models(Settings.builder().put(models));
        return this;
    }

    public PutModelsRequest models(Settings.Builder models) {
        this.models = models.normalizePrefix("models.").build();
        return this;
    }

    public PutModelsRequest models(String source) {
        models(Settings.settingsBuilder().loadFromSource(source));
        return this;
    }

    public PutModelsRequest models(Map<String, Map<String, Object>> models) {
        try {
            XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
            builder.startObject();
            builder.startObject("models");
            for (Map.Entry<String, Map<String, Object>> entry : models.entrySet()) {
                builder.field(entry.getKey());
                builder.map(entry.getValue());
            }
            builder.endObject();
            builder.endObject();
            models(builder.string());
        } catch (IOException e) {
            throw new ElasticsearchGenerationException("Failed to generate [" + models + "]", e);
        }
        return this;
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        models = readSettingsFromStream(in);
        readTimeout(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        writeSettingsToStream(models, out);
        writeTimeout(out);
    }
}
