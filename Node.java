import java.net.InetAddress;
import java.util.Date;

public class Node {
    private String hostname;
    private Date timestamp;
    private String services;
    private InetAddress ipv6Address;

    // Constructor
    public Node(String hostname, Date timestamp, String services) {
        this.hostname = hostname;
        this.timestamp = timestamp;
        this.services = services;
    }

    void printDetails() {
        System.out.println("hostname " + hostname);
        System.out.println("timestamp " + timestamp);
        System.out.println("services " + services);
    }

    public String getHostname() {
        return hostname;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public String getServices() {
        return services;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public void setServices(String services) {
        this.services = services;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

}
