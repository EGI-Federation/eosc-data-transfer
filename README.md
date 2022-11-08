# EOSC Data Transfer Service

[EOSC Future](https://eoscfuture.eu) is an EU-funded Horizon 2020 project that is implementing
the [European Open Science Cloud](https://eosc-portal.eu) (EOSC). EOSC will give European
researchers access to a wide web of [FAIR data](https://en.wikipedia.org/wiki/FAIR_data)
and related services.

This project builds a generic data transfer service that can be used in EOSC to transfer
large amounts of data to cloud storage, by just indicating the source and destination.
The EOSC Data Transfer Service features a [RESTful](https://restfulapi.net) Application
Programming Interface (REST API). 

The API covers three sets of functionalities:

- [Parsing digital object identifiers](#parsing-dois)
- [Creating and managing data transfers](#creating-and-managing-data-transfers)
- [Managing storage elements](#managing-storage-elements)

This project uses [Quarkus](https://quarkus.io/), the Supersonic Subatomic Java Framework.


## Authentication and authorization

All three groups of API endpoints mentioned above support may require authorization.
The generic data transfer service behind the EOSC Data Transfer API aims to be agnostic
with regard to authorization, thus the HTTP header `Authorization` (if present) will be
forwarded as received.

> Note that the frontend using this API might have to supply more than one set of credentials:
> (1) one for the data repository (determined by the DOI used as the source), (2) one for
> the transfer service that is automatically selected when a destination is chosen, and (3) one
> for the destination storage system. Only (2) is mandatory.

The API endpoints that parse DOIs usually call APIs that are open access, however the
HTTP header `Authorization` (if present) will be forwarded as received. This ensures that
the EOSC Data Transfer API can be [extended with new parsers](#integrating-new-doi-parsers)
for data repositories that require authorization.

The API endpoints that create and manage transfers, as well as the ones that manage storage
elements, do require authorization, in the form of an access token passed via the HTTP
header `Authorization`. This gets passed to the actual [transfer service registered to handle
the selected destination storage](#register-new-destinations-serviced-by-the-new-data-transfer-service).
The challenge is that some storage systems used as the target of the transfer may also
require authentication and/or authorization. Thus, an additional set of credentials may be
needed to be supplied to the endpoints in this group.

> For example, for transfers to [EGI dCache](https://www.dcache.org), the configured transfer service
> that handles the transfers is [EGI Data Transfer](https://www.egi.eu/service/data-transfer/).
> These both can use the same EGI Check-in access token, thus no additional credentials are needed
> besides the access token for the transfer service.

TODO: Describe how FTP username/password and S3 keys are passed.

## Parsing DOIs

The API supports parsing digital object identifier (DOIs) and will return a list of files
in the repository indicated by the DOI. It will automatically identify the DOI type and will
use the correct service to retrieve the list of source files.

> DOIs are persistent identifiers (PIDs) dedicated to identification of content over digital networks.
> These are registered by one of the
> [registration agencies of the International DOI Foundation](https://www.doi.org/registration_agencies.html).
> Although in this documentation we refer to DOIs, the API endpoint that parses DOIs
> supports any PID registered in the [global handle system](https://www.dona.net/handle-system)
> of the [DONA Foundation](https://www.dona.net/digitalobjectarchitecture).


### Supported data repositories

The API supports parsing DOIs, to the following data repositories:
- [Zenodo](https://zenodo.org/)
- [B2SHARE](https://eudat.eu/catalogue/B2SHARE)
- Any repository that supports [Signposting](https://signposting.org) 


### Integrating new DOI parsers

The API endpoint `GET /parser` that parses DOIs is extensible. All you have to do is implement the
generic parser interface for a specific data repository, then register your class implementing the
interface in the configuration.

#### 1. Implement the interface for a generic DOI parser

Implement the interface `ParserService` in a class of your choice. 

Your class must have a constructor that receives a `String id`, which must be returned by the method `getId()`.

When the API `GET /parser` is called to parse a DOI, all configured parsers will be tried,
by calling the method `canParseDOI()`, until one is identified that can parse the DOI. If no
parser can handle the DOI, the API fails. In case you cannot determine if your parser can
handle a DOI just from the URL, you can use the passed in `ParserHelper` to check if the URL
redirects to the data repository your parser supports.

After a parser is identified, the methods `init()` and `parseDOI()` are called in order.

> The same `ParserHelper` is used when trying all parsers for a DOI. This helper caches the
> redirects, so you should check with `getRedirectedToUrl()` before incurring one or more
> network calls by calling `checkRedirect()`.

#### 2. Add configuration for the new DOI parser

Add a new entry in the [configuration file](#configuration) under `proxy/parsers` for the
new parser service, with the following settings:

- `name` is the human-readable name of this data source.
- `class` is the canonical Java class name that implements the interface `ParserService` for this data source.
- `url` is the base URL for the REST client that will be used to call the API of this data source (optional).
- `timeout` is the maximum timeout in milliseconds for calls to the data source.
  If not supplied, the default value 5000 (5 seconds) is used.


## Creating and managing data transfers

The API supports creation of new data transfer jobs, finding data transfers, querying information
about data transfers, and canceling data transfers.

The API also supports managing files and folders in a destination storage. Each data transfer service
that gets integrated can optionally implement this functionality. Clients can query if this
functionality is implemented for a destination storage by using the endpoint `GET /storage/info`.

Every API endpoint that performs operations or queries on data transfers or on storage elements
in a destination storage has to be passed a destination type. This selects the data transfer
service that will be used to perform the data transfer, freeing the clients of the API from
having to know which data transfer service to pick for each destination.

> Note that if you do not supply the "dest" query parameter when making an API call
> to perform a transfer or a storage element related operation or query, the default value
> "_dcache_" will be supplied instead.

### Supported transfer destinations

Initially, [EGI Transfer Service](https://docs.egi.eu/users/data/management/data-transfer/) is integrated into the
EOSC Data Transfer API, supporting the following destination storages:

- [EGI dCache](https://www.dcache.org) by passing "_dcache_" as the "dest" query parameter to the API


### Integrating new data transfer services

The API for creating and managing data transfers is extensible. All you have to do is implement the
generic data transfer interface for a specific data transfer service, then register your class
implementing the interface as the handler for one or more destinations.

#### 1. Implement the interface for a generic data transfer service

Implement the interface `TransferService` in a class of your choice.

> The method `canBrowseStorage()` signals to the frontend whether browsing the
> destination storage is supported for the destination(s) registered for this data transfer service.

#### 2. Add configuration for the new data transfer service

Add a new entry in the [configuration file](#configuration) under `proxy/transfer/services` for the
new transfer service, with the following settings:

- `name` is the human-readable name of this transfer service. 
- `url` is the base URL for the REST client that will be used to call the API of this transfer service. 
- `class` is the canonical Java class name that implements the interface `TransferService` for this transfer service. 
- `timeout` is the maximum timeout in milliseconds for calls to the transfer service.
   If not supplied, the default value 5000 (5 seconds) is used.  

#### 3. Register new destinations serviced by the new data transfer service 

Add one or more entries in the [configuration file](#configuration) under `proxy/transfer/destinations`,
one for each destination for which this transfer service will be used to perform data transfers.  

#### 4. Add the new destinations in the enum of possible destination 

In the enum `DataTransferBase.Destination` add new values for each of the destinations for which
the new data transfer implementation shall be used. Use the same values as the names of the keys
in the previous step (3).


## Configuration

The application configuration file is in `src/main/resources/application.yml`.

> The settings that are supposed to be overridable with environment variables should also
> be added to `src/main/resources/application.properties`.


## Running the API in dev mode

You can run your application in dev mode that enables live coding using:
```shell script
./mvnw compile quarkus:dev
```

Then open the Dev UI, which is available in dev mode only at http://localhost:8080/q/dev/.

## Packaging and running the API

The application can be packaged using:
```shell script
./mvnw package
```
It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:
```shell script
./mvnw package -Dquarkus.package.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Running the API using Docker Compose

You can use Docker Compose to easily deploy and run the EOSC Data Transfer API.
This will run two containers:

- This application that implements the API and serves it over HTTP on port 8080 ([configurable](#configuration))
- HTTPS frontend that will forward REST API requests to the API application

Steps to run the API in a container:

1. Copy the file `src/main/docker/.env.template` to `src/main/docker/.env` and edit the
environment variables `SERVICE_DOMAIN` and `SERVICE_URL` to be the domain name and the
fully qualified URL (including the protocol HTTPS, the domain name and the port) at which
the API will be available. Also provide and email address that will be used, together with the domain name, to
request an SSL certificate for the webserver serving the API.

2. Run the command `build.sh` (or `build.cmd` on Windows) to build and run the containers that implement
the EOSC Data Transfer API.  

3. The HTTPS frontend container will automatically use [Let's Encrypt](https://letsencrypt.org)
to request an SSL certificate for HTTPS.

After the HTTPS container is deployed and working properly, connect to the container and
make sure it is requesting an actual HTTPS certificate. By default, it will use a self-signed
certificate and will only do dry runs for requesting a certificate. This is so to avoid the
[rate limits](https://letsencrypt.org/docs/rate-limits/) of Let's Encrypt. To do this:
 
- Run the command `sudo docker exec -it data-transfer-cert /bin/sh` then
- In the container change directory `cd /opt`
- Edit the file `request.sh` and remove the `certbot` parameter `--dry-run` 

> In case you remove the containers of the EOSC Data Transfer API, retain the volume `data_transfer_cert`,
> which contains the SSL certificate. This will avoid requesting a new one for the same domain, in case
> you redeploy the API (prevents exceeding Let's Encrypt rate limit).


## Related Guides

- [REST client implementation](https://quarkus.io/guides/rest-client-reactive): REST client to easily call REST APIs
- [REST server implementation](https://quarkus.io/guides/rest-json): REST endpoint framework to implement REST APIs
- [REST Easy Reactive](https://quarkus.io/guides/resteasy-reactive) Writing reactive REST services
- [YAML Configuration](https://quarkus.io/guides/config#yaml): Use YAML to configure your application
- [Swagger UI](https://quarkus.io/guides/openapi-swaggerui): Add user-friendly UI to view and test your REST API
- [Mutiny Guides](https://smallrye.io/smallrye-mutiny/guides): Reactive programming with Mutiny
- [Optionals](https://dzone.com/articles/optional-in-java): How to use Optional in Java
