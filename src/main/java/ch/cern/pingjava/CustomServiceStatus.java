package ch.cern.pingjava;

public class CustomServiceStatus {

    private String areWeGood;
    private String name;
    private int replicas;

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

    public int getReplicas() {
        return replicas;
    }

    public void setReplicas(int replicas) {
        this.replicas = replicas;
    }
}
