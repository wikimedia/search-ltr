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

public class FeaturesState extends AbstractDiffable<Custom> implements Custom {
    public static final String TYPE = "ltr-features";
    public static final FeaturesState PROTO = new FeaturesState();
    private final ImmutableMap<String, ImmutableMap<String, Settings>> features;

    public FeaturesState() {
        features = ImmutableMap.<String, ImmutableMap<String, Settings>>of();
    }

    public FeaturesState(Map<String, Map<String, Settings>> features) {
        ImmutableMap.Builder<String, ImmutableMap<String, Settings>> builder = ImmutableMap.builder();
        for (Map.Entry<String, Map<String, Settings>> entry : features.entrySet()) {
            builder.put(entry.getKey(), ImmutableMap.copyOf(entry.getValue()));
        }
        this.features = builder.build();
    }

    public FeaturesState(ImmutableMap.Builder<String, ImmutableMap<String, Settings>> builder) {
        this.features = builder.build();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Map<String, Settings>> features() {
        // Seems a bit insane ... but is the only way i can tell to have the
        // explicit immutable
        // guarantee for cluster state, while having a generic output type. Safe
        // because it's
        // all immutable.
        return (Map<String, Map<String, Settings>>) (Object) features;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        FeaturesState that = (FeaturesState) o;

        if (!features.equals(that.features)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return features.hashCode();
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public String toString() {
        return features.toString();
    }

    @Override
    public FeaturesState readFrom(StreamInput in) throws IOException {
        int outerSize = in.readVInt();
        ImmutableMap.Builder<String, ImmutableMap<String, Settings>> outerBuilder = ImmutableMap.builder();
        for (int i = 0; i < outerSize; i++) {
            int innerSize = in.readVInt();
            ImmutableMap.Builder<String, Settings> innerBuilder = ImmutableMap.builder();
            for (int j = 0; j < innerSize; j++) {
                final String featureName = in.readString();
                innerBuilder.put(featureName, readSettingsFromStream(in));
            }
            outerBuilder.put(in.readString(), innerBuilder.build());
        }
        ;
        return new FeaturesState(outerBuilder);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        out.writeVInt(features.size());
        for (Map.Entry<String, ImmutableMap<String, Settings>> outerEntry : features.entrySet()) {
            out.writeVInt(outerEntry.getValue().size());
            for (Map.Entry<String, Settings> innerEntry : outerEntry.getValue().entrySet()) {
                out.writeString(innerEntry.getKey());
                writeSettingsToStream(innerEntry.getValue(), out);
            }
            out.writeString(outerEntry.getKey());
        }
    }

    @Override
    public XContentBuilder toXContent(XContentBuilder builder, ToXContent.Params params) throws IOException {
        for (Map.Entry<String, ImmutableMap<String, Settings>> outerEntry : features.entrySet()) {
            builder.startObject(outerEntry.getKey());
            for (Map.Entry<String, Settings> innerEntry : outerEntry.getValue().entrySet()) {
                builder.startObject(innerEntry.getKey());
                innerEntry.getValue().toXContent(builder, params);
                builder.endObject();
            }
            builder.endObject();
        }
        return builder;
    }

    public static class Builder {
        private Map<String, Map<String, Settings>> map = new HashMap<>();

        public Builder(FeaturesState featuresState) {
            if (featuresState != null) {
                putAll(featuresState.features());
            }
        }

        public Builder removeAll(Settings features) {
            for (Map.Entry<String, Settings> entry : features.getGroups("features").entrySet()) {
                for (String featureStoreName : entry.getValue().getAsStructuredMap().keySet()) {
                    remove(featureStoreName, entry.getValue().getAsArray(featureStoreName));
                }
            }
            return this;
        }

        public Builder removeAll(Map<String, List<String>> features) {
            for (Map.Entry<String, List<String>> entry : features.entrySet()) {
                remove(entry.getKey(), entry.getValue());
            }
            return this;
        }

        public Builder remove(String featureStoreName, String... features) {
            remove(featureStoreName, Arrays.asList(features));
            return this;
        }

        public Builder remove(String featureStoreName, List<String> features) {
            Map<String, Settings> inner = map.get(featureStoreName);
            if (inner != null) {
                for (String feature : features) {
                    inner.remove(feature);
                }
            }
            return this;
        }

        public Builder putAll(Settings features) {
            for (Map.Entry<String, Settings> entry : features.getGroups("features").entrySet()) {
                final String featureStoreName = entry.getKey();
                for (String featureName : entry.getValue().getAsStructuredMap().keySet()) {
                    put(featureStoreName, featureName, entry.getValue().getAsSettings(featureName));
                }
            }
            return this;
        }

        public Builder putAll(Map<String, Map<String, Settings>> features) {
            for (Map.Entry<String, Map<String, Settings>> entry : features.entrySet()) {
                putAll(entry.getKey(), entry.getValue());
            }
            return this;
        }

        public Builder putAll(String featureStoreName, Map<String, Settings> features) {
            Map<String, Settings> inner = map.get(featureStoreName);
            if (inner == null) {
                map.put(featureStoreName, new HashMap<String, Settings>(features));
            } else {
                inner.putAll(features);
            }
            return this;
        }

        public Builder put(String featureStoreName, String feature, Settings settings) {
            Map<String, Settings> inner = map.get(featureStoreName);
            if (inner == null) {
                inner = new HashMap<>();
                inner.put(feature, settings);
                map.put(featureStoreName, inner);
            } else {
                inner.put(feature, settings);
            }
            return this;
        }

        public FeaturesState build() {
            return new FeaturesState(map);
        }
    }
}
