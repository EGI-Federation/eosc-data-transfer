#
# 1.--- Build Java app ---------------------------------------------------
#
FROM maven:3-eclipse-temurin-17 AS builder

# Get source code
WORKDIR /app

RUN git clone https://github.com/EGI-Federation/eosc-future-data-transfer.git . && \
    chmod a+x mvnw.sh

# Build app
RUN mvn package --define skipTests


#
# 2.--- Build container  --------------------------------------------------
#
FROM registry.access.redhat.com/ubi8/openjdk-17:1.11

ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en'

# We make four distinct layers so if there are application changes the library layers can be re-used
COPY --from=builder --chown=185 /app/target/quarkus-app/lib/ /deployments/lib/
COPY --from=builder --chown=185 /app/target/quarkus-app/*.jar /deployments/
COPY --from=builder --chown=185 /app/target/quarkus-app/app/ /deployments/app/
COPY --from=builder --chown=185 /app/target/quarkus-app/quarkus/ /deployments/quarkus/

# If you want to include the debug port into your docker image
# you will have to expose the debug port (default 5005) like this :  EXPOSE 8080 5005

EXPOSE 8080
USER 185

ENV JAVA_OPTS="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENV JAVA_APP_JAR="/deployments/quarkus-run.jar"

# This image uses the `run-java.sh` script to run the application.
# This scripts computes the command line to execute your Java application, and
# includes memory/GC tuning.
# You can configure the behavior using the environment variables from file data-transfer.env
# See also https://github.com/fabric8io-images/run-java-sh


