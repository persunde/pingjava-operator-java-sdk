package ch.cern.pingjava;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.containersolutions.operator.api.Context;
import com.github.containersolutions.operator.api.Controller;
import com.github.containersolutions.operator.api.ResourceController;
import com.github.containersolutions.operator.api.UpdateControl;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A very simple sample controller that creates a service with a label.
 */
@Controller(customResourceClass = CustomService.class,
        crdName = "customservices.sample.javaoperatorsdk")
public class CustomServiceController implements ResourceController<CustomService> {

    public static final String KIND = "CustomService";
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final KubernetesClient kubernetesClient;

    public CustomServiceController(KubernetesClient kubernetesClient) {
        this.kubernetesClient = kubernetesClient;
    }

    @Override
    public boolean deleteResource(CustomService resource, Context<CustomService> context) {
        log.info("Execution deleteResource for: {}", resource.getMetadata().getName());
        kubernetesClient.services().inNamespace(resource.getMetadata().getNamespace())
                .withName(resource.getMetadata().getName()).delete();
        return true;
    }

    /**
     * Main Operator function. It is called by the Java--Operator-SDK framework whenever a change happens to
     * the CustomResource that is beeing watched. You are supposed to put the logic in here.
     * I have made it so that it scales the deployment up or down, based on the "size" that is defined in the
     * CustomResource, this is done with the class ServiceSpec, that has the spec attributes: "name", "label" and "size"
     * The size is changed by another thread, that updates the CustomResource with the new size number, thus causing
     * an event to happen and this function is called becasue of the change to the CR.
     * @param resource
     * @param context
     * @return
     */
    @Override
    public UpdateControl<CustomService> createOrUpdateResource(CustomService resource, Context<CustomService> context) {
        log.info("Execution createOrUpdateResource for: {}", resource.getMetadata().getName());

        ServicePort servicePort = new ServicePort();
        servicePort.setPort(8080);
        ServiceSpec serviceSpec = new ServiceSpec();
        serviceSpec.setPorts(Collections.singletonList(servicePort));

        CustomServiceStatus status = new CustomServiceStatus();
        CustomServiceStatus oldStatus = resource.getStatus();
        if (oldStatus != null) {
            status.setName(resource.getStatus().getName());
        } else {
            status.setName("status");
        }
        status.setAreWeGood("Yes!");
        resource.setStatus(status);

        int resourceSize = resource.getSpec().getSize();
        log.info("Number of replicas in spec: {}", resourceSize);
        try {
            /* Updates the deployment with the replica size as defined by the CR */
            updateDeploymentReplicaCount(resourceSize);
        } catch (IOException e) {
            e.printStackTrace();
        }

        /*
        * TODO: remove this service, no need
        */
        kubernetesClient.services().inNamespace(resource.getMetadata().getNamespace()).createOrReplaceWithNew()
                .withNewMetadata()
                .withName(resource.getSpec().getName())
                .addToLabels("testLabel", resource.getSpec().getLabel())
                .endMetadata()
                .withSpec(serviceSpec)
                .done();
        try {
            createOrReplaceDeployment();
        } catch (IOException e) {
            e.printStackTrace();
            log.error("createOrReplaceDeployment failed", e);
            throw new RuntimeException(e);
        }
        return UpdateControl.updateCustomResource(resource);
    }

    private static int testnum = 10;

    /**
     * Called by a looping thread. It checks the latency of the Webservers and changes the "size" attribute
     * in the CustomResource. This causes an event to happen, and createOrUpdateResource() is called by the framework
     * because of this update to the CR.
     */
    public void checkStatus() {
        String crdYamlPath = "crd.yaml";
        try (InputStream yamlInputStream = getClass().getResourceAsStream(crdYamlPath)) {
            CustomResourceDefinition customResourceDefinition = kubernetesClient.customResourceDefinitions().load(yamlInputStream).get();
            CustomResourceDefinitionContext customResourceDefinitionContext = CustomResourceDefinitionContext.fromCrd(customResourceDefinition);
            Map<String, Object> customResourceObject = kubernetesClient.customResource(customResourceDefinitionContext).get("default", "custom-service1"); /* Name is found in CustomService.yaml, can be dynamically fetched as wellm jsut lazy here */
            try {
                long latency = getLatencyMilliseconds();
                int latencyScaleUpLimit = 750;
                int latencyScaleDownLimit = 150;
                if (latency > latencyScaleUpLimit) {
                    testnum++;
                } else if (latency < latencyScaleDownLimit) {
                    if (testnum > 0) {
                        testnum--;
                    }
                }
                Map<String, Object> status = (Map<String, Object>) customResourceObject.get("status");
                status.put("replicas", testnum);
                customResourceObject.put("status", status);

                /**
                 * Updates the CustomResource with the new values, this is what will trigger the event that calls createOrUpdateResource()
                 * NOTE: You can add a "typed" API, but I implemeted the dirty and quick typeless solution, see the two links for more info:
                 *  https://github.com/fabric8io/kubernetes-client/blob/master/doc/CHEATSHEET.md#customresource-typed-api
                 *  https://github.com/fabric8io/kubernetes-client/blob/master/doc/CHEATSHEET.md#customresource-typeless-api
                 */
                Map<String, Object> object = kubernetesClient.customResource(customResourceDefinitionContext).get("default", "custom-service1");
                ((HashMap<String, Object>)object.get("spec")).put("size", Integer.toString(testnum));
                /*((HashMap<String, Object>)object.get("status")).put("replicas", Integer.toString(testnum));*/
                object = kubernetesClient.customResource(customResourceDefinitionContext).edit("default", "custom-service1", new ObjectMapper().writeValueAsString(object));
            } catch (IOException e) {
                e.printStackTrace();
                log.error("getLatency | scaleUp | scaleDown failed", e);
                throw new RuntimeException(e);
            }
        } catch (IOException e) {
            e.printStackTrace();
            log.error("checkStatus() failed", e);
        }
    }

    /**
     * Updates the deployment with the new replica count, if the new count is different from the current one
     * @param newReplicasCount
     * @throws IOException
     */
    private void updateDeploymentReplicaCount(int newReplicasCount) throws IOException {
        String deploymentYamlPath = "stresstest-deploy.yaml";
        try (InputStream yamlInputStream = getClass().getResourceAsStream(deploymentYamlPath)) {
            Deployment originalDeployment = kubernetesClient.apps().deployments().load(yamlInputStream).get();
            String nameSpace = originalDeployment.getMetadata().getNamespace();
            String name = originalDeployment.getMetadata().getName();
            Deployment currentDeploy = kubernetesClient.apps().deployments().inNamespace(nameSpace).withName(name).get();
            if (    currentDeploy != null &&
                    currentDeploy.getSpec() != null &&
                    currentDeploy.getSpec().getReplicas() != newReplicasCount
            ) {
                /* Updates the replica count */
                Deployment updatedDeploy = kubernetesClient.apps().deployments().inNamespace(nameSpace)
                        .withName(name).edit()
                        .editSpec().withReplicas(newReplicasCount).endSpec().done();
            }
        } catch (IOException e) {
            e.printStackTrace();
            log.error("scaleUp failed", e);
            throw e;
        }
    }

    /**
     * Creates the deployment organized and monitored by this Operator
     * @throws IOException
     */
    private void createOrReplaceDeployment() throws IOException {
        String deploymentYamlPath = "stresstest-deploy.yaml";
        try (InputStream yamlInputStream = getClass().getResourceAsStream(deploymentYamlPath)) {
            Deployment aDeployment = kubernetesClient.apps().deployments().load(yamlInputStream).get();
            String nameSpace = aDeployment.getMetadata().getNamespace();
            String name = aDeployment.getMetadata().getName();
            Deployment currentDeploy = kubernetesClient.apps().deployments().inNamespace(nameSpace).withName(name).get();
            if (currentDeploy == null) {
                Deployment createdDeployment = kubernetesClient.apps().deployments().inNamespace(aDeployment.getMetadata().getNamespace()).createOrReplace(aDeployment);
                log.info("Created deployment: {}", deploymentYamlPath);
                /* Create a Service for the deployment as well */
                createService(nameSpace);
            }
        } catch (IOException ex) {
            log.error("createOrReplaceDeployment failed", ex);
            throw ex;
        }
    }

    /**
     * Creates the service for the deployment managed by the Operator
     * NOTE: I think there is a bug here, that it does not work properly. Apply the Service before running the Operator to be sure.
     * @param namespace
     * @throws IOException
     */
    private void createService(String namespace) throws IOException {
        String serviceYamlPath = "CustomService.yaml";
        try (InputStream yamlInputStream = getClass().getResourceAsStream(serviceYamlPath)) {
            Service aService = kubernetesClient.services().load(yamlInputStream).get();
            Service createdSvc = kubernetesClient.services().inNamespace(namespace).createOrReplace(aService);
            String serviceName = createdSvc.getMetadata().getName();
            log.info("Created Service: {}", serviceName);
        }
    }

    /**
     * Gets the latency from the webservers.
     * NOTE: The webserver deployment must be deployed before this function is called. It is not handled by the operator as of now.
     * @return The latency in milliseconds, should be between 0-1000, as the webservers are set to sleep between 0-10 seconds.
     * @throws IOException
     */
    private long getLatencyMilliseconds() throws IOException {
        String webserverServiceSERVICEHOST = System.getenv("WEBSERVER_SERVICE_SERVICE_HOST");
        String webserverServiceSERVICEPORT = System.getenv("WEBSERVER_SERVICE_SERVICE_PORT");
        String url = "http://" + webserverServiceSERVICEHOST + ":" + webserverServiceSERVICEPORT;

        long start = System.currentTimeMillis();
        String result = executeGet(url);
        log.info("HTTP GET result :\n" + result + "\n------------\n");
        long latency = System.currentTimeMillis() - start;

        return latency;
    }

    /**
     * Helper function for getLatencyMilliseconds(). Does the actual http GET call to the webserver(s).
     * @param urlToRead
     * @return
     * @throws IOException
     */
    private String executeGet(String urlToRead) throws IOException {
        StringBuilder result = new StringBuilder();
        URL url = new URL(urlToRead);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        String line;
        while ((line = rd.readLine()) != null) {
            result.append(line);
        }
        rd.close();
        return result.toString();
    }
}
