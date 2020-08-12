package ch.cern.pingjava;

import com.github.containersolutions.operator.api.Context;
import com.github.containersolutions.operator.api.Controller;
import com.github.containersolutions.operator.api.ResourceController;
import com.github.containersolutions.operator.api.UpdateControl;
import com.google.gson.Gson;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.DoneableCustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.CustomResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.dsl.Resource;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;
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

        String resourceReplicas = status.getReplicas();
        log.info("99999 Number of replicas in resource: {}", resourceReplicas);
        /**
         * TODO: Check that the number of resourceReplicas matches current replicas in deployment
         */

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
    public void checkStatus() {
        String crdYamlPath = "crd.yaml";
        try (InputStream yamlInputStream = getClass().getResourceAsStream(crdYamlPath)) {
            CustomResourceDefinition customResourceDefinition = kubernetesClient.customResourceDefinitions().load(yamlInputStream).get();
            CustomResourceDefinitionContext customResourceDefinitionContext = CustomResourceDefinitionContext.fromCrd(customResourceDefinition);

            Map<String, Object> customResourceObject = kubernetesClient.customResource(customResourceDefinitionContext).get("default", "customservices.sample.javaoperatorsdk");
            Gson gson = new Gson();
            String json = gson.toJson(customResourceObject);
            try {
                long latency = getLatencyMilliseconds();
                int latencyScaleUpLimit = 750;
                int latencyScaleDownLimit = 150;
                if (latency > latencyScaleUpLimit) {
                    //scaleUp();
                    // Update status
                    Map<String, Object> result = kubernetesClient.customResource(customResourceDefinitionContext).updateStatus("replicas", Integer.toString(testnum++), json);
                } else if (latency < latencyScaleDownLimit) {
                    //scaleDown();
                    if (testnum > 0) {
                        testnum--;
                    }
                    Map<String, Object> result = kubernetesClient.customResource(customResourceDefinitionContext).updateStatus("replicas", Integer.toString(testnum), json);
                }
            } catch (IOException e) {
                e.printStackTrace();
                log.error("getLatency | scaleUp | scaleDown failed", e);
                throw new RuntimeException(e);
            }
        } catch (IOException e) {
            e.printStackTrace();
            log.error("getLatency | scaleUp | scaleDown failed", e);
            throw new RuntimeException(e);
        }
    }

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

    private void createService(String namespace) throws IOException {
        String serviceYamlPath = "CustomService.yaml";
        try (InputStream yamlInputStream = getClass().getResourceAsStream(serviceYamlPath)) {
            Service aService = kubernetesClient.services().load(yamlInputStream).get();
            Service createdSvc = kubernetesClient.services().inNamespace(namespace).createOrReplace(aService);
            String serviceName = createdSvc.getMetadata().getName();
            log.info("Created Service: {}", serviceName);
        }
    }
    private void scaleUp() throws IOException {
        /* TODO: update the CR instead of scaling directly here */
        String deploymentYamlPath = "stresstest-deploy.yaml";
        try (InputStream yamlInputStream = getClass().getResourceAsStream(deploymentYamlPath)) {
            Deployment originalDeployment = kubernetesClient.apps().deployments().load(yamlInputStream).get();
            String nameSpace = originalDeployment.getMetadata().getNamespace();
            String name = originalDeployment.getMetadata().getName();
            Deployment currentDeploy = kubernetesClient.apps().deployments().inNamespace(nameSpace).withName(name).get();
            if (currentDeploy != null && currentDeploy.getSpec() != null) {
                int newReplicasCount = currentDeploy.getSpec().getReplicas() + 1;
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

    private void scaleDown() throws IOException {
        /* TODO: update the CR instead of scaling directly here */
        String deploymentYamlPath = "stresstest-deploy.yaml";
        try (InputStream yamlInputStream = getClass().getResourceAsStream(deploymentYamlPath)) {
            Deployment originalDeployment = kubernetesClient.apps().deployments().load(yamlInputStream).get();
            String nameSpace = originalDeployment.getMetadata().getNamespace();
            String name = originalDeployment.getMetadata().getName();
            Deployment currentDeploy = kubernetesClient.apps().deployments().inNamespace(nameSpace).withName(name).get();
            if (currentDeploy != null && currentDeploy.getSpec() != null) {
                int newReplicasCount = currentDeploy.getSpec().getReplicas() - 1;
                if (newReplicasCount >= 0) {
                    /* Updates the replica count */
                    Deployment updatedDeploy = kubernetesClient.apps().deployments().inNamespace(nameSpace)
                            .withName(name).edit()
                            .editSpec().withReplicas(newReplicasCount).endSpec().done();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            log.error("scaleDown failed", e);
            throw e;
        }
    }

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
