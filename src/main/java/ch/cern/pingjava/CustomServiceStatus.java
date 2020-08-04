package ch.cern.pingjava;

import io.fabric8.kubernetes.api.model.ServiceStatus;

public class CustomServiceStatus extends ServiceStatus {

    public CustomServiceStatus() {
        super();
    }

    private String areWeGood;

    public String getAreWeGood() {
        return areWeGood;
    }

    public void setAreWeGood(String areWeGood) {
        setAdditionalProperty("areWeGood", areWeGood);
        this.areWeGood = areWeGood;
    }
}
