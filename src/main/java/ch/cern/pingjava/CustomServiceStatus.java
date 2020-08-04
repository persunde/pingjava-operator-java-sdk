package ch.cern.pingjava;

import io.fabric8.kubernetes.api.model.ServiceStatus;

public class CustomServiceStatus {

    private String areWeGood;
    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getAreWeGood() {
        return areWeGood;
    }

    public void setAreWeGood(String areWeGood) {
        this.areWeGood = areWeGood;
    }
}
