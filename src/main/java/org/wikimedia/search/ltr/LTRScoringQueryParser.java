package org.wikimedia.search.ltr;

import java.io.IOException;

import org.apache.lucene.search.Query;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.index.query.QueryParseContext;
import org.elasticsearch.index.query.QueryParser;
import org.elasticsearch.index.query.QueryParsingException;
import org.wikimedia.search.ltr.model.LTRScoringModel;
import org.wikimedia.search.ltr.store.LTRStoreService;

public class LTRScoringQueryParser implements QueryParser {
    public static final String NAME = "ltr";

    final private LTRStoreService store;

    @Inject
    public LTRScoringQueryParser(LTRStoreService store) {
        this.store = store;
    }

    @Override
    public String[] names() {
        return new String[] { NAME, "learn_to_rank", "learnToRank" };
    }

    @Override
    public Query parse(QueryParseContext parseContext) throws IOException, QueryParsingException {
        XContentParser parser = parseContext.parser();

        XContentParser.Token token;
        String currentFieldName = null;
        String modelName = null;
        boolean extractAllFeatures = false;
        FeatureLogger logger = null;
        Settings efi = Settings.EMPTY;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token.isValue()) {
                if ("model".equals(currentFieldName)) {
                    modelName = parser.text();
                } else if ("extract_all_features".equals(currentFieldName)
                    || "extractAllFeatures".equals(currentFieldName)) {
                    extractAllFeatures = parser.booleanValue();
                } else {
                    throw new QueryParsingException(parseContext,
                        "[ltr] query does not support [" + currentFieldName + "]");
                }
            } else if (token == XContentParser.Token.START_ARRAY) {
                throw new QueryParsingException(parseContext, "[ltr] query does not support array values");
            } else if (token == XContentParser.Token.START_OBJECT) {
                if ("logger".equals(currentFieldName)) {
                    logger = parseLogger(parseContext, parser);
                } else if ("efi".equals(currentFieldName)) {
                    efi = parseEfi(parseContext, parser);
                } else {
                    throw new QueryParsingException(parseContext,
                        "[ltr] query does not support objects in [" + currentFieldName + "]");
                }
            } else {
                throw new QueryParsingException(parseContext, "[ltr] unexpected token");
            }
        }

        if (modelName == null) {
            throw new QueryParsingException(parseContext, "learn_to_rank requires 'model' to be specified");
        }
        LTRScoringModel model = store.getModel(modelName);
        if (model == null) {
            throw new QueryParsingException(parseContext, "[ltr] unknown model [" + modelName + "]");
        }
        LTRScoringQuery query = new LTRScoringQuery(model, efi, extractAllFeatures,
            parseContext.indexQueryParserService());
        if (logger != null) {
            query.setFeatureLogger(logger);
        }
        return query;
    }

    private FeatureLogger parseLogger(QueryParseContext parseContext, XContentParser parser)
        throws IOException, QueryParsingException {
        XContentParser.Token token = null;
        String currentFieldName = null;
        String stringFormat = "json";
        String featureFormat = "sparse";
        String marker = null;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token.isValue()) {
                if ("stringFormat".equals(currentFieldName) || "string_format".equals(currentFieldName)) {
                    stringFormat = parser.text();
                } else if ("featureFormat".equals(currentFieldName) || "feature_format".equals(currentFieldName)) {
                    featureFormat = parser.text();
                } else if ("marker".equals(currentFieldName)) {
                    marker = parser.text();
                } else {
                    throw new QueryParsingException(parseContext,
                        "[ltr] query does not support [logger." + currentFieldName + "]");
                }
            } else {
                throw new QueryParsingException(parseContext, "[ltr] unexpected token");
            }
        }
        return FeatureLogger.createFeatureLogger(stringFormat, featureFormat, marker);
    }

    private Settings parseEfi(QueryParseContext parseContext, XContentParser parser)
        throws IOException, QueryParsingException {
        XContentParser.Token token = null;
        Settings.Builder efi = Settings.builder();
        String currentFieldName = null;
        while ((token = parser.nextToken()) != XContentParser.Token.END_OBJECT) {
            if (token == XContentParser.Token.FIELD_NAME) {
                currentFieldName = parser.currentName();
            } else if (token.isValue()) {
                efi.put(currentFieldName, parser.text());
            } else {
                throw new QueryParsingException(parseContext, "[ltr] query only supports strings in efi");
            }
        }
        return efi.build();
    }
}
