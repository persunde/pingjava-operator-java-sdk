package ch.cern.pingjava;

import io.fabric8.kubernetes.api.model.ServiceStatus;
import io.fabric8.kubernetes.client.CustomResource;

public class CustomService extends CustomResource {

    private ServiceSpec spec;
    private CustomServiceStatus status;

    public ServiceSpec getSpec() {
        return spec;
    }

    public void setSpec(ServiceSpec spec) {
        this.spec = spec;
    }

    public CustomServiceStatus getStatus() { return status; }

    public void setStatus(CustomServiceStatus status) {
        this.status = status;
    }
}
