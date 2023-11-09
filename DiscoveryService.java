import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketAddress;

public class DiscoveryService {

    private final MulticastSocket multicastSocket;
    private final SocketAddress multicastGroup;
    private final String serviceAdvertisement;
    private final long advertisementPeriod;

    public DiscoveryService(String multicastAddress, int multicastPort, String serviceAdvertisement,
            long advertisementPeriod) throws IOException {
        // Create a multicast socket and join the multicast group
        multicastSocket = new MulticastSocket(null);
        multicastGroup = new InetSocketAddress(InetAddress.getByName(multicastAddress), multicastPort);
        this.serviceAdvertisement = serviceAdvertisement;
        this.advertisementPeriod = advertisementPeriod;
        String networkInterfaceName = "enp4s0"; // Specify your network interface

        // Set the network interface for multicast (e.g., eth0)
        NetworkInterface networkInterface = NetworkInterface.getByName(networkInterfaceName);

        System.out.println(
                "Joining multicast group: " + multicastAddress + ":" + multicastPort + " on " + networkInterfaceName);

        multicastSocket.joinGroup(multicastGroup, networkInterface);
    }

    public void startAdvertising() {
        // Periodically send advertisement messages
        while (true) {
            try {
                byte[] advertisementData = (serviceAdvertisement + "\n").getBytes();

                // multicastSocket.setTimeToLive(64); // Set

                multicastSocket.send(new DatagramPacket(advertisementData,
                        advertisementData.length, multicastGroup));

                System.out.println(advertisementData);
                Thread.sleep(advertisementPeriod);
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void stopAdvertising() {
        // Stop advertising and leave the multicast group when done
        multicastSocket.close();
    }

    public static void main(String[] args) throws IOException {
        String multicastAddress = "ff02::fb"; // Match the service's multicast address

        System.out.println(multicastAddress);

        int multicastPort = 49153; // Your multicast port
        String serviceAdvertisement = "advertisement:id=your_id:timestamp=your_timestamp:search=path-filename,download=true";

        long advertisementPeriod = 5000; // Advertisement period in milliseconds

        DiscoveryService discoveryService = new DiscoveryService(multicastAddress,
                multicastPort, serviceAdvertisement,
                advertisementPeriod);
        discoveryService.startAdvertising();

    }
}
