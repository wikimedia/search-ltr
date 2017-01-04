Learning to Rank
================

The Learning to Rank (LTR) rescore query provides a way for you to extract
features directly inside Elasticsearch for use in training a machine learned
model. You can then deploy that model to Elasticsearch and use it to rerank
your top X search results.

This plugin is based on the ltr plugin for SolrCloud.

# Defining Features
In the learning to rank plugin, you can define features in a feature space
using standard Elasticsearch queries. As an example:

###### features.json
```json
{
    "_DEFAULT_": {
        "isBook": {
            "class": "org.wikimedia.search.ltr.feature.ESQueryFeature",
            "params": {
                "query": {
                    "constant_score": {
                        "filter": {
                            "match": {
                                "category": "book"
                            }
                        }
                    }
                }
            }
        },
        "documentRecency": {
            "class": "org.wikimedia.search.ltr.feature.ESQueryFeature",
            "params": {
                "query": {
                    "function_score": {
                        "query": { "match_all": {} },
                        "gauss": {
                            "publish_date": "8w"
                        }
                    }
                }
            }
        },
        "userTextTitleMatch": {
            "class": "org.wikimedia.search.ltr.feature.ESQueryFeature",
            "params": {
                "query": {
                    "match": {
                        "title": "${user_text}"
                    }
                }
            }
        },
        "userFromMobile": {
            "class": "org.wikimedia.saerch.ltr.feature.ExternalValueFeature",
            "params": {
                "externalValue": "userFromMobile",
                "required": true
            }
        }
    }
}
```

Defines four features in the `fstore1` feature store. Anything that is a valid 
Elasticsearch query can be used to define a feature.

### Feature Stores

TODO: document

### Elasticsearch Query Features

#### Binary features
The first feature isBook fires if the term 'book' matches the category field
for the given examined document. Since this feature uses a constant_score query
either the score 1 (in case of a match) or the score 0 (in case of no match)
will be returned.

#### Recency features
In the second feature (documentRecency) a function_score query was specified
with a gauss decay. In this case the score for the feature on a given document
is whatever the query returns(TODO: explain score for this specific query).

### External features
Users can specify external information that can be passed in as part of the
query to the ltr ranking framework.  In the third (userTextTitleMatch) feature
the query will be looking for an external field called 'user_text' passed
through in the request, and will fire if there is a term match for the document
field 'title' from the value of the external field user_text. If the external
feature is not passed it will throw an exception.  TODO: Support defaults

The fourth feature (userFromMobile) will be looking for an external parameter
called 'userFromMobile' passed in through the request, if the
ExternalValueFeature is provided required=true, it will throw an exception if
the external feature is not passed. When required=false it will silently ignore
the feature and avoid the scoring (the model will consider 0 as the feature
value). The advantage in defining a feature as not required, where possible, is
to avoid wasting caching space and time in calculating the featureScore. See
the [Run a Rerank query](#run-a-rerank-query) section for how to pass in
external information.

### Custom Features
Custom features can be created by extending from
org.wikimedia.search.ltr.feature.Feature, however this is generally not
recommended. The majority of features should be possible to create using the
methods described above.

# Defining Models
Currently the Learning to Rank plugin supports 2 generalized form of models: 1. Linear Model i.e. [RankSVM](http://www.cs.cornell.edu/people/tj/publications/joachims_02c.pdf), [Pranking](https://papers.nips.cc/paper/2023-pranking-with-ranking.pdf)) and 2. Multiple Additive Trees i.e. [LambdaMART](http://research.microsoft.com/pubs/132652/MSR-TR-2010-82.pdf), [Gradient Boosted Regression Trees (GBRT)](https://papers.nips.cc/paper/3305-a-general-boosting-method-and-its-application-to-learning-ranking-functions-for-web-search.pdf)

### Linear
If you'd like to introduce a bias set a constant feature to the bias value
you'd like and make a weight of 1.0 for that feature

###### model.json
```
{
    "myModelName": {
        "class": "org.wikimedia.search.ltr.model.LinearModel",
        "features": [
            { "name": "userTextTitleMatch" },
            { "name": "isBook" }
        ],
        "params": {
            "weights": {
                "userTextTitleMatch": 1.0,
                "isBook": 0.1
            }
        }
    }
}
```

This is an example of a toy Linear model. Class specifies the class to be using
to interpret the model. The object key is the model identifier you will use
when making requests to the ltr query. Features specifies the feature space
that you want extracted when using this model. All features that appear in the
model params will be used for scoring and must appear in the features list. You
can add extra features to the features list that will be computed but not used
in the model for scoring, which can be useful for logging. Params are the
Linear parameters. 

A good library for training SVM, an example of a Linear model, is
(https://www.csie.ntu.edu.tw/~cjlin/liblinear/, https://www.csie.ntu.edu.tw/~cjlin/libsvm/).
You will need to convert the libSVM model format to the format specified
above.

### Multiple Additive Trees


###### model2.json
```json
{
    "multipleadditivetreesmodel": {
        "class": "org.wikimedia.search.ltr.model.MultipleAdditiveTreesModel",
        "features": [
            { "name": "userTextTitleMatch" },
            { "name": "isBook" }
        ],
        "params": {
            "trees": [
                {
                    "weight": 1,
                    "root": {
                        "feature": "userTextTitleMatch",
                        "threshold": 0.5,
                        "left": {
                            "value": -100
                        }
                        "right": {
                            "feature": "isBook",
                            "threshold": 0.5,
                            "left": {
                                "value": 50
                            }
                            "right": {
                                "value": 75
                            }
                        }
                    }
                },
                {
                    "weight": 2,
                    "root": {
                        "value": -10
                    }
                }
            ]
        }
    }
}
```

This is an example of a toy Multiple Additive Trees. Class specifies the class
to be using to interpret the model. Object key is the model identifier you will
use when issuing an ltr query. Features specifies the feature space that you
want extracted when using this model. All features that appear in the model
params will be used for scoring and must appear in the features list. You can
add extra features to the features list that will be computed but not used in
the model for scoring, which can be usedful for logging. Params are the
Multiple Additive Trees specific parameters. In this case we have 2 trees, one
with 3 leaf nodes and one with 1 leaf node.

A good library for training LambdaMART, an example of MultipleAdditiveTrees, is
(http://sourceforge.net/p/lemur/wiki/RankLib/, https://github.com/Microsoft/LightGBM,
and https://github.com/dmlc/xgboost). You will need to convert the model format to
the format specified above.

# Deploy Models and Features
To send features run

`curl -XPUT http://localhost:9200/_ltr/features --data-binary @/path/features.json'`

To send models run
`curl -Xput http://localhost:9200/_ltr/models --data-binary @/path/model.json'`

# View Models and Features
`curl -XGET http://localhost:9200/_ltr/features`

`curl -XGET http://localhost:9200/_ltr/models`

# Run a Rerank Query
Provide the ltr query as the rescore_query
```json
{
    "query": {
        "match": {
            "_all": "Casablanca"
        },
    },
    "rescore": [
        {
            "window_size": 100,
            "query": {
                "query_weight": 0,
                "rescore_query_weight": 1,
                "score_mode": "total",
                "rescore_query": {
                    "ltr": {
                        "model": "myModelName"
                    }
                }
            }
        }
    ]
}
```

The model name is the name of the model you sent to Elasticsearch earlier.  The
number of documents you want reranked is specified in the window_size.

### Pass in external information for external features
Add to your rescore_query
`{ "ltr": { "model": "myModelName", "efi": { "field1": "text1", "field2": "text2" } } }`

Where "field1" specifies the name of the customized field to be used by one or
more of your features, and text1 is the information to pass in. As an example
that matches the earlier shown userTextTitleMatch feature one could do:

`{ "ltr": { "model": "myModelName", "efi": { "user_text": "Casablanca", "user_intent": "movie"} } }`

# Extract features
To extract features you need to enable logging in the ltr query.

{ "ltr": { "model": "myModelName", "logging": { "marker": "Casablanca" } } }

This will write features out to log4j on org.wikimedia.search.ltr.FeatureLogger
channel.  This is sub-par, but gets the job done for the moment.

# Assemble training data
In order to train a learning to rank model you need training data. Training
data is what "teaches" the model what the appropriate weight for each feature
is. In general training data is a collection of queries with associated
documents and what their ranking/score should be. As an example:
```
secretary of state|John Kerry|0.66|CROWDSOURCE
secretary of state|Cesar A. Perales|0.33|CROWDSOURCE
secretary of state|New York State|0.0|CROWDSOURCE
secretary of state|Colorado State University Secretary|0.0|CROWDSOURCE

microsoft ceo|Satya Nadella|1.0|CLICK_LOG
microsoft ceo|Microsoft|0.0|CLICK_LOG
microsoft ceo|State|0.0|CLICK_LOG
microsoft ceo|Secretary|0.0|CLICK_LOG
```
In this example the first column indicates the query, the second column
indicates a unique id for that doc, the third column indicates the relative
importance or relevance of that doc, and the fourth column indicates the
source. There are 2 primary ways you might collect data for use with your
machine learning algorithm. The first is to collect the clicks of your users
given a specific query. There are many ways of preparing this data to train a
model (http://www.cs.cornell.edu/people/tj/publications/joachims_etal_05a.pdf,
http://olivier.chapelle.cc/pub/DBN_www2009.pdf). The general idea is that if a
user sees multiple documents and clicks the one lower down, that document
should be scored higher than the one above it. The second way is explicitly
through a crowdsourcing platform like Mechanical Turk or CrowdFlower.These
platforms allow you to show human workers documents associated with a query and
have them tell you what the correct ranking should be.

At this point you'll need to collect feature vectors for each query document
pair.  You can use the information from the Extract features section above to
do this. A simple method is to generate id-filter queries for elasticsearch
that choose the documents you have scores for on a query, and a ltr-rescore
with a window larger that the provided list of document ids.

# Explanation of the core reranking logic
An LTR model is plugged into the rescorer through a standard elasticsearch query.
The plugin will read from the request the model, an instance of 
[LTRScoringQuery](/src/main/java/org/wikimedia/search/ltr/LTRScoringQuery.java), plus other
parameters. The LTRScoringQuery will take care of computing the values of all the
[features](/src/main/java/org/wikimedia/search/ltr/feature/Feature.java) and then will
delegate the final score generation to the 
[LTRScoringModel](/src/main/java/org/wikimedia/search/ltr/model/LTRScoringModel.java).

# Speeding up the weight creation with threads

TODO: Not yet implemented


