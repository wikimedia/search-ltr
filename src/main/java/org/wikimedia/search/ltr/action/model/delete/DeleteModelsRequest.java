package org.wikimedia.search.ltr.action.model.delete;

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

public class DeleteModelsRequest extends AcknowledgedRequest<DeleteModelsRequest> {
    private Settings models = EMPTY_SETTINGS;

    public DeleteModelsRequest() {
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

    public DeleteModelsRequest models(Settings models) {
        models(Settings.builder().put(models));
        return this;
    }

    public DeleteModelsRequest models(Settings.Builder models) {
        this.models = models.normalizePrefix("models.").build();
        return this;
    }

    public DeleteModelsRequest models(String source) {
        models(Settings.settingsBuilder().loadFromSource(source));
        return this;
    }

    public DeleteModelsRequest models(List<Map<String, Object>> models) {
        try {
            XContentBuilder builder = XContentFactory.contentBuilder(XContentType.JSON);
            builder.startObject();
            builder.startArray("models");
            for (Map<String, Object> model : models) {
                builder.map(model);
            }
            builder.endArray();
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
