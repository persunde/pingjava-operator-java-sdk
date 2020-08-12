package ch.cern.pingjava;

public class CustomServiceStatus {

    private String areWeGood;
    private String name;
    private String replicas;

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

    public String getReplicas() {
        return replicas;
    }

    public void setReplicas(String replicas) { this.replicas = replicas; }

}
