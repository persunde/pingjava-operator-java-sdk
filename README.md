# pingjava-operator-java-sdk
Testing out how to build a Java Operator with the Operator-Java-sdk

## Build and upload docker-image 
This command will build and push the newly created image to hub.docker.io.<br>
<strong>NOTE:</strong> Edit the *Makefile* and the *pom.xml* to change the docker-image name and push to your own repo.
```bash
make docker
```

## Run
First apply the CustomResourceDefinition and the CustomResource to the K8S-Cluster. It is needed before you run the Operator!
This is only needed to be done once.
```bash
kubectl apply -f crd/crd.yaml
kubectl apply -f crd/CustomService.yaml
```

Now you can deploy the Operator. If you make any changes to the Operator, build and run this command again:
```bash
kubectl apply -f deployment/deploy_operator.yaml
```

To delete the Operator and the deployment created by the Operator:
```bash
kubectl delete -f deployment/deploy_operator.yaml
kubectl delete deployment stresstest-ping
```


## Notes
You can edit the code and change the retry interval in *Runner.java*. 
You can set the maximum retry-attempts and the milliseconds between each interval (aka ms between each call to the Controllers function *createOrUpdateResource()*).
```java
new GenericRetry().withLinearRetry().setMaxAttempts(maxAttempts).setInitialInterval(milliseconds);
```

##  How to read the updates from the Operator -> posted to the CustomResource 
Run this command to ses updates to the CustomResource that is updated by the Operator:
```bash
kubectl get CustomService/custom-service1 -o yaml
```