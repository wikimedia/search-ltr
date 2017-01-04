package org.wikimedia.search.ltr.store;

import static org.elasticsearch.common.settings.Settings.readSettingsFromStream;
import static org.elasticsearch.common.settings.Settings.writeSettingsToStream;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.elasticsearch.cluster.AbstractDiffable;
import org.elasticsearch.cluster.ClusterState.Custom;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.ToXContent;
import org.elasticsearch.common.xcontent.XContentBuilder;

import com.google.common.collect.ImmutableMap;

public class ModelsState extends AbstractDiffable<Custom> implements Custom {
    public static final String TYPE = "ltr-models";
    public static final ModelsState PROTO = new ModelsState();
    private final ImmutableMap<String, Settings> models;

    public ModelsState() {
        models = ImmutableMap.<String, Settings>of();
    }

    public ModelsState(ImmutableMap<String, Settings> models) {
        this.models = models;
    }

    public Map<String, Settings> models() {
        return models;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        ModelsState that = (ModelsState) o;

        if (!models.equals(that.models)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return models.hashCode();
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String toString() {
        return models.toString();
    }

    @Override
    public ModelsState readFrom(StreamInput in) throws IOException {
        ImmutableMap.Builder<String, Settings> builder = ImmutableMap.builder();
        int size = in.readVInt();
        for (int i = 0; i < size; i++) {
            final String modelName = in.readString();
            builder.put(modelName, readSettingsFromStream(in));
        }
        return new ModelsState(builder.build());
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeVInt(models.size());
        for (Map.Entry<String, Settings> entry : models.entrySet()) {
            out.writeString(entry.getKey());
            ;
            writeSettingsToStream(entry.getValue(), out);
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        for (Map.Entry<String, Settings> entry : models.entrySet()) {
            builder.startObject(entry.getKey());
            entry.getValue().toXContent(builder, params);
            builder.endObject();
        }
        return builder;
    }

    public static class Builder {
        private Map<String, Settings> map = new HashMap<>();

        public Builder(ModelsState modelsState) {
            if (modelsState != null) {
                putAll(modelsState.models());
            }
        }

        public Builder removeAll(Settings models) {
            return removeAll(models.getAsArray("models"));
        }

        public Builder removeAll(String... modelNames) {
            return removeAll(Arrays.asList(modelNames));
        }

        public Builder removeAll(List<String> modelNames) {
            for (String modelName : modelNames) {
                map.remove(modelName);
            }
            return this;
        }

        public Builder putAll(Settings models) {
            map.putAll(models.getGroups("models"));
            return this;
        }

        public Builder putAll(Map<String, Settings> models) {
            map.putAll(models);
            return this;
        }

        public ModelsState build() {
            return new ModelsState(ImmutableMap.copyOf(map));
        }
    }
}
