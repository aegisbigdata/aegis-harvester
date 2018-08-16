# Importer

Prepares weather data for further processing by either fetching data periodically from the [OpenWeatherMap API](https://openweathermap.org/api)
or providing a file upload for CSV data.

## Setup

1. Install importer
    - Clone repository
    - Navigate into the cloned directory
    - Copy the sample configuration `cp conf/config.sample.json conf/config.json`
    - Edit the configuration to your liking:

        |Key|Description|
        |:--- |:---|
        |http.port| The port this service will run on |
        |target.host| The host of the service the importer will push it's data to |
        |target.port| The port of the service the importer will push it's data to |
        |target.endpoint| The relative URI of the service the importer will push it's data to |
        |owmApiKey| Your OpenWeatherMap API key |

2. Start the application
    - Vanilla: `mvn clean package && java -jar target/importer-fat.jar`
    - Docker
        1. Start your docker daemon
        2. Build the application: `mvn clean package`
        3. Adjust the port number (`EXPOSE` in the `Dockerfile`)
        4. Build the image: `docker build -t aegis/importer .`
        5. Run the image, adjusting the port number as set in step _iii_: `docker run -i -p 8123:8123 aegis/importer`

## API

### OpenWeatherMap

To trigger the fetching of data a `POST` request must be sent to

    {url}/owm

containing a JSON body with the data shown in the table below. All values are mandatory, except user and password.

|Key|Description|
|:--- |:---|
|pipeId| Unique url identifying the job |
|hopsProjectId| The project ID in hopsworks in which the hopsDataset resides |
|hopsDataset| The dataset in hopsworks to upload the resulting CSV file to |
|type| Either `bbox` or `location` |
|url| Either the bbox coordinates or a location ID |
|durationInHours| How long the pipeline should run for, in hours. A url < 1 will make the pipeline run exactly once. |
|frequencyInMinutes| The interval at which data should be fetched, in minutes. A url < 1 will make the pipeline not run. |
|user| The user account for hopsworks. |
|password| The user password for hopsworks. |

A frequency higher than the duration (for durations > 0) is not allowed.

### Ckan

To trigger the fetching of data a `POST` request must be sent to

    {url}/ckan

containing a JSON body with the data shown in the table below. All values are mandatory, except user and password.

|Key|Description|
|:--- |:---|
|pipeId| Unique url identifying the job |
|hopsProjectId| The project ID in hopsworks in which the hopsDataset resides |
|hopsDataset| The dataset in hopsworks to upload the resulting CSV file to |
|url| The full ckan URL to fetch API data from |
|durationInHours| How long the pipeline should run for, in hours. A url < 1 will make the pipeline run exactly once. |
|frequencyInMinutes| The interval at which data should be fetched, in minutes. A url < 1 will make the pipeline not run. |
|user| The user account for hopsworks. |
|password| The user password for hopsworks. |

A frequency higher than the duration (for durations > 0) is not allowed.

### Push CSV

In order to push weather data from another source a `POST` request must be sent to

    {url}/upload

containing multipart/form-data with key-url pairs containing the data shown in the table below. All values are mandatory, except user and password.

|Key|Description|
|:--- |:---|
|pipeId| Unique url identifying the job |
|hopsProjectId| The project ID in hopsworks in which the hopsDataset resides |
|hopsDataset| The dataset in hopsworks to upload the resulting CSV file to |
|upload| Path to a local file |
|mapping| Instructions for transforming csv data. See below for a more thorough explanation |
|user| The user account for hopsworks. |
|password| The user password for hopsworks. |

##### Transform CSV

The supplied CSV file can be transformed in various ways.    
Each transformation needs to be specified in a mapping script.
The transformations will be executed in the order they are listed.
A sample JSON structure for each transformation type is shown below.
Numbers indicate the indices of the columns to be transformed.

1. Rename headers

        {
            "renameHeaders" : [
                {   
                    "old": "oldHeader",
                    "new": "newHeader
                }
            ]   
        }

2. Convert timestamps

        {
            "convertTimestamps" : [ 1, 5, 8 ]   
        }

3. Switch columns

        {
            "switchColumns" : [
                [1, 2],
                [2, 3]
            ]
        }


4. Merge columns

        {
            "mergeColumns" : [
                [ 1, 2, 3 ],
                [ 4, 5, 6 ]               
            ]
        }

5. Split CSV file

        {
            "splitColumn" : 1
        }        

##### Sample call

An example using curl is shown below:

    curl -X POST {url}/upload -F pipeId=csvPipe -F hopsProjectId=123 -F hopsDataset=myFolder -F mapping='{"splitByColumn":2, "renameHeaders":[{"old":"trip_id","new":"tripId"}], "mergeColumns":[[2, 3]], "convertTimeStamps":[5]}' -F upload=@/path/to/file.csv

### Suite5 Events

To trigger the fetching of data a `POST` request must be sent to

    {url}/event

containing a JSON body with the data shown in the table below. All values are mandatory, except user and password.

|Key|Description|
|:--- |:---|
|pipeId| Unique url identifying the job |
|hopsProjectId| The project ID in hopsworks in which the hopsDataset resides |
|hopsDataset| The dataset in hopsworks to upload the resulting CSV file to |
|url| JSON as specified in the `openapi.yaml` file |
|user| The user account for hopsworks. |
|password| The user password for hopsworks. |

A frequency higher than the duration (for durations > 0) is not allowed.

### Status

A list of currently running pipes can be obtained by sending a `GET` request to

    {url}/running

A list of the current component state can be obtained by sending a `GET` request to

    {url}/state
