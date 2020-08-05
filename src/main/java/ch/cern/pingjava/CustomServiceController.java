package ch.cern.pingjava;

import com.github.containersolutions.operator.api.Context;
import com.github.containersolutions.operator.api.Controller;
import com.github.containersolutions.operator.api.ResourceController;
import com.github.containersolutions.operator.api.UpdateControl;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServiceSpec;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Collections;

/**
 * A very simple sample controller that creates a service with a label.
 */
@Controller(customResourceClass = CustomService.class,
        crdName = "customservices.sample.javaoperatorsdk")
public class CustomServiceController implements ResourceController<CustomService> {

    public static final String KIND = "CustomService";
    private final static Logger log = LoggerFactory.getLogger(CustomServiceController.class);

    private int counter = 0;

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

        /*
        * TODO: call a K8S-Service for the Ping server and calculate the latency. Then scale up or down
        */

        try {
            long latency = getLatencyMilliseconds();
            int latencyScaleUpLimit = 700;
            int latencyScaleDownLimit = 400;
            if (latency > latencyScaleUpLimit) {
                scaleUp();
            } else if (latency < latencyScaleDownLimit) {
                scaleDown();
            }
        } catch (IOException e) {
            e.printStackTrace();
            log.error("getLatency | scaleUp | scaleDown failed", e);
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

    private void createOrReplaceDeployment() throws IOException {
        String deploymentYamlPath = "stresstest-deploy.yaml";
        try (InputStream yamlInputStream = getClass().getResourceAsStream(deploymentYamlPath)) {
            Deployment aDeployment = kubernetesClient.apps().deployments().load(yamlInputStream).get();
            Deployment createdDeployment = kubernetesClient.apps().deployments().inNamespace(aDeployment.getMetadata().getNamespace()).createOrReplace(aDeployment);
            log.info("Created deployment: {}", deploymentYamlPath);
        } catch (IOException ex) {
            log.error("createOrReplaceDeployment failed", ex);
            throw ex;
        }
    }

    private void scaleUp() throws IOException {
        String deploymentYamlPath = "stresstest-deploy.yaml";
        try (InputStream yamlInputStream = getClass().getResourceAsStream(deploymentYamlPath)) {
            Deployment originalDeployment = kubernetesClient.apps().deployments().load(yamlInputStream).get();
            String nameSpace = originalDeployment.getMetadata().getNamespace();
            String name = originalDeployment.getMetadata().getName();
            Deployment currentDeploy = kubernetesClient.apps().deployments().inNamespace(nameSpace).withName(name).get();
            int newReplicasCount = currentDeploy.getSpec().getReplicas() + 1;

            /* Updates the replica count */
            Deployment updatedDeploy = kubernetesClient.apps().deployments().inNamespace(nameSpace)
                    .withName(name).edit()
                    .editSpec().withReplicas(newReplicasCount).endSpec().done();

        } catch (IOException e) {
            e.printStackTrace();
            log.error("scaleUp failed", e);
            throw e;
        }
    }

    private void scaleDown() throws IOException {
        String deploymentYamlPath = "stresstest-deploy.yaml";
        try (InputStream yamlInputStream = getClass().getResourceAsStream(deploymentYamlPath)) {
            Deployment originalDeployment = kubernetesClient.apps().deployments().load(yamlInputStream).get();
            String nameSpace = originalDeployment.getMetadata().getNamespace();
            String name = originalDeployment.getMetadata().getName();
            Deployment currentDeploy = kubernetesClient.apps().deployments().inNamespace(nameSpace).withName(name).get();

            int newReplicasCount = currentDeploy.getSpec().getReplicas() - 1;
            if (newReplicasCount >= 0) {
                /* Updates the replica count */
                Deployment updatedDeploy = kubernetesClient.apps().deployments().inNamespace(nameSpace)
                        .withName(name).edit()
                        .editSpec().withReplicas(newReplicasCount).endSpec().done();
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

    private static String executeGet(String urlToRead) throws IOException {
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
