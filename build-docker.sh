./gradlew :build
 cp ./src/main/Dockerfile ./build
 docker build -t nrwljohanna/testing:test-kotlin-s3 ./build
