# pingjava-operator-java-sdk
Testing out how to build a Java Operator with the Operator-Java-sdk

## Build and upload docker-image 
This command will build and push the newly created image to hub.docker.io.<br>
<strong>NOTE:</strong> Edit the *Makefile* and the *pom.xml* to change the docker-image name and push to your own repo.
```bash
make docker
```

## Prerequisites
First apply the CustomResourceDefinition and the CustomResource to the K8S-Cluster. It is needed before you run the Operator!
This is only needed to be done once.
```bash
kubectl apply -f crd/crd.yaml
kubectl apply -f crd/CustomService.yaml
```

This Operator pings a Webserver-Service that is in the K8S Cluster. 
You need to deploy the webservers and the webserver-service first.   
```bash
kubectl apply -f deployment/webserver-deployment.yaml
kubectl apply -f service/webserver-service.yaml
```

## Run the Operator
Now you can deploy the Operator. If you make any changes to the Operator, build and run this command again:
```bash
kubectl apply -f deployment/deploy_operator.yaml
```

## Delete Operator
To clean up and delete everything run these commands:
```bash
kubectl delete -f deployment/
kubectl delete -f service/
kubectl delete -f crd/
kubectl delete deployment stresstest-ping
```
 
##  How to read the updates from the Operator -> posted to the CustomResource 
Run this command to ses updates to the CustomResource that is updated by the Operator:
```bash
kubectl get CustomService/custom-service1 -o yaml
```

## Notes
The java-operator-sdk will soon have implemented a interval timer, but it is currently not implemented. 
The devs told me it will come soon.
Currently I have implemented it myself by forking a new thread and checking the latency of the webserver every 5 second.