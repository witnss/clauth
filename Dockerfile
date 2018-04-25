FROM alpine:3.4

RUN apk update && apk upgrade && apk add bash openssl openjdk8

# install leiningen by copying the wrapper script into $PATH
COPY bin/lein /usr/bin/lein
# get the service wait script in here too
COPY bin/wait-for-it.sh /usr/bin/wait-for-it.sh
# initialize lein here so we don't spend a lot of time fetching deps on start
RUN lein upgrade

# install the server files and go to there.
RUN mkdir -p /var/srv
COPY . /var/srv
WORKDIR /var/srv
# fetch deps at build time
RUN lein -U deps

CMD /bin/bash
