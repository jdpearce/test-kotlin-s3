FROM openjdk:14
RUN mkdir -p /srv/nx-api
WORKDIR /srv/nx-api
COPY ./libs/nx-api-all.jar nx-api.jar
COPY ./libs/aggregator-all.jar aggregator.jar
EXPOSE 8080
CMD java -Xmx800m -jar ./nx-api.jar
