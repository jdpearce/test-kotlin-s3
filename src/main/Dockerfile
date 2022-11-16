FROM openjdk:14
RUN mkdir -p /srv/test-kotlin-s3
WORKDIR /srv/test-kotlin-s3
COPY ./libs/*-all.jar test-kotlin-s3.jar
CMD java -Xmx800m -jar ./test-kotlin-s3.jar
