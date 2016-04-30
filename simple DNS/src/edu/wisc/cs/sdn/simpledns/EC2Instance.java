package edu.wisc.cs.sdn.simpledns;

/**
 * Created by cook on 4/30/2016.
 */
public class EC2Instance {
    private String ip;
    private String location;

    EC2Instance(String ip, String location) {
        this.ip = ip;
        this.location = location;
    }

    public String getIp() {
        return this.ip;
    }

    public String getLocation() {
        return this.location;
    }

    @Override
    public String toString() {
        return this.ip + " " + this.location;
    }
}
