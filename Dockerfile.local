FROM java:7

# RUN useradd -m tcdash
# USER tcdash

COPY . /usr/src
WORKDIR /usr/src
EXPOSE 3000
RUN LEIN_ROOT=true bin/lein ring uberjar
ENV PORT 3000
CMD java -jar target/clubby-standalone.jar
