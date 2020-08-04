# pingjava-operator-java-sdk
Testing out how to build a Java Operator with the Operator-Java-sdk

## Build and upload docker-image 
This command will build and push the newly created image to hub.docker.io.<br>
<strong>NOTE:</strong> Edit the *Makefile* and the *pom.xml* to change the docker-image name and push to your own repo.
```bash
make docker
```

## Run
First apply the CustomResourceDefinition to the K8S-Cluster. It is needed before you run the Operator!
This is only needed to be done once.
```bash
kubectl apply -f crd/crd.yaml
```

Then you can deploy the Operator: 
```bash
kubectl apply -f deployment/deploy_operator.yaml
```

