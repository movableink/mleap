# MLeap Serving

MLeap serving provides a lightweight docker image for setting up RESTful
API services with MLeap models. It is meant to be very simple and used
as a tool to get prototypes up and running quickly.

## Installation

MLeap serving is a Docker image hosted on [Docker Hub](https://hub.docker.com/r/combustml/mleap-serving/).

To get started, pull the image to your local machine:

```
docker pull combustml/mleap-serving:0.6.0-SNAPSHOT
```

## Usage

In order to start using your models as a REST API, we will need to:

1. Start the server in Docker
2. Load our model into memory
3. Transform a leap frame

### Start Server

First let's start the Docker image so we can start transforming data.
Make sure to mount a directory containing your models on the host
machine into the container. In this example, we will be storing our
models in `/tmp/models` and mounting it in the container at `/models`.

```
mkdir /tmp/models
docker run -p 65327:65327 -v /tmp/models:/models combustml/mleap-serving:0.6.0-SNAPSHOT
```

This will expose the model server locally on port `65327`.

### Load Model

Use curl to load the model into memory. If you don't have your own
model, download one of our example models. Make sure to place it in the
models directory you mounted when starting the server.

1. [AirBnB Linear Regression](https://s3-us-west-2.amazonaws.com/mleap-demo/airbnb.model.lr-0.6.0-SNAPSHOT.zip)
2. [AirBnB Random Forest](https://s3-us-west-2.amazonaws.com/mleap-demo/airbnb.model.rf-0.6.0-SNAPSHOT.zip)

```
curl -XPUT -H "content-type: application/json" \
  -d '{"path":"/models/<my model>.zip"}' \
  http://localhost:65327/model
```

### Retrieve Model Schema

```
curl -XGET -H "content-type: application/json" \
  http://localhost:65327/model
```

### Transform

Next we will use our model to transform a JSON-encoded leap frame. If
you are using our AirBnB example models, you can download the leap frame
here:

1. [AirBnB Leap Frame](https://s3-us-west-2.amazonaws.com/mleap-demo/frame.airbnb.json)

Save the frame to `/tmp/frame.airbnb.json` and then let's transform it
using our server.

```
curl -XPOST -H "accept: application/json" \
  -H "content-type: application/json" \
  -d @/tmp/frame.airbnb.json \
  http://localhost:65327/transform
```

You should get back a result leap frame, as JSON, that you can then
extract the result from. If you used one of our example AirBnB models,
the last field in the leap frame will be the prediction.

### Unload Model

If for some reason you don't want any model to be loaded into memory,
but keep the server running, just DELETE the `model` resource:

```
curl -XDELETE http://localhost:65327/model
```


### Loading and transforming with multiple models:

1. Clone repo
    ```
    git clone git@github.com:movableink/mleap.git
    ```

1. Check out correct branch:
    ```
    cd mleap
    git checkout serve-multiple-models
    ```

1. Build from source (http://mleap-docs.combust.ml/getting-started/building.html):
    ```
    git submodule init
    git submodule update
    sbt compile
    sbt test
    ``` 

1. Copy external jar to mleap-serving/lib directory
    ```
    mkdir mleap-serving/lib
    cp ~/dev/mleap-transformers/target/scala-2.11/mleap-transformers-assembly-1.0.jar mleap-serving/lib
    ```

1. Create docker image locally
    ```
    sbt mleap-serving/docker:publishLocal
    ```

1. Copy MLeap model bundles to /tmp/models
    ```
    mkdir /tmp/models
    cp ~/dev/data-science/mleap_runtime_test/files/\*.zip /tmp/models
    ```

1. Create docker container
    ```
    docker run -d --rm -p 65327:65327 -v /tmp/models:/models combustml/mleap-serving:0.8.2-SNAPSHOT
    ```

1. Load models into endpoints
    ```
    curl -XPUT -H "content-type: application/json" -d '{"path":"/models/model_4894_mleap.zip"}' http://localhost:65327/model_4894
    curl -XPUT -H "content-type: application/json" -d '{"path":"/models/model_6317_mleap.zip"}' http://localhost:65327/model_6317
    curl -XPUT -H "content-type: application/json" -d '{"path":"/models/model_6757_mleap.zip"}' http://localhost:65327/model_6757
    curl -XPUT -H "content-type: application/json" -d '{"path":"/models/model_6323_mleap.zip"}' http://localhost:65327/model_6323
    ```

1. Transform an input
    ```
    curl -XPOST -H "accept: application/json" -H "content-type: application/json" -d @/Users/melaniefreed/dev/data-science/mleap_runtime_test/files/test_input_4894_0.05_9ed07318c7fab562eb65009828fa2a752d30ee4.json http://localhost:65327/transform_4894
    ```

