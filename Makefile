all: install docker

install:
	mvn install
docker:
	mvn dockerfile:build && docker push persundecern/pingjava-operator
