package org.wikimedia.search.ltr.action.feature.put;

import java.io.IOException;

import org.elasticsearch.action.support.master.AcknowledgedResponse;
import org.elasticsearch.common.io.stream.StreamInput;
import org.elasticsearch.common.io.stream.StreamOutput;

public class PutFeaturesResponse extends AcknowledgedResponse {

    public PutFeaturesResponse() {
    }

    public PutFeaturesResponse(boolean acknowledged) {
        super(acknowledged);
    }

    @Override
    public void readFrom(StreamInput in) throws IOException {
        super.readFrom(in);
        readAcknowledged(in);
    }

    @Override
    public void writeTo(StreamOutput out) throws IOException {
        super.writeTo(out);
        writeAcknowledged(out);
    }
}
