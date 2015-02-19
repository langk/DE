#!/bin/sh

set -e
set -x

CMD=$1

if [ -z $CMD ]; then
    CMD=test
fi

if [[ "$(uname)" == 'Darwin' ]]; then
    $(boot2docker shellinit)
fi

if [ $(docker ps | grep de-db | wc -l) -gt 0 ]; then 
    docker kill de-db
fi

if [ $(docker ps -a | grep de-db | wc -l) -gt 0 ]; then
    docker rm de-db
fi

docker run --name de-db -e POSTGRES_PASSWORD=notprod -d -p 5432:5432 discoenv/de-db
sleep 2
docker run --rm --link de-db:postgres discoenv/de-db-loader
docker run -i -t --rm -v $(pwd):/build -v ~/.m2:/root/.m2 -w /build --link de-db:postgres clojure lein $CMD
