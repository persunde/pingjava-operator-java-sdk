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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.Arrays;
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
        status.setAreWeGood("Yes!");
        resource.setStatus(status);

        kubernetesClient.services().inNamespace(resource.getMetadata().getNamespace()).createOrReplaceWithNew()
                .withNewMetadata()
                .withName(resource.getSpec().getName())
                .addToLabels("testLabel", resource.getSpec().getLabel())
                .endMetadata()
                .withSpec(serviceSpec)
                .done();
        try {
            createOrReplaceDeployment();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            log.error("createOrReplaceDeployment failed", e);
        }
        return UpdateControl.updateCustomResource(resource);
    }

    public void createOrReplaceDeployment() throws FileNotFoundException {
        String deploymentYamlPath = "stresstest-deploy.yaml";
        ClassLoader classloader = Thread.currentThread().getContextClassLoader();
        InputStream yamlInputStream = classloader.getResourceAsStream(deploymentYamlPath);

        Deployment aDeployment = kubernetesClient.apps().deployments().load(yamlInputStream).get();
        Deployment createdDeployment = kubernetesClient.apps().deployments().inNamespace(aDeployment.getMetadata().getNamespace()).createOrReplace(aDeployment);
        log.info("Created deployment: {}", deploymentYamlPath);
    }
}
