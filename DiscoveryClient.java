import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.NetworkInterface;
import java.net.SocketAddress;

public class DiscoveryClient {
    public static void main(String[] args) {
        String multicastAddress = "ff02::fb"; // Match the service's multicast address
        int multicastPort = 49153; // Match the service's multicast port
        String networkInterfaceName = "enp4s0"; // Specify your network interface

        try {
            NetworkInterface networkInterface = NetworkInterface.getByName(networkInterfaceName);

            if (networkInterface == null) {
                System.err.println("Network interface not found.");
                System.exit(1);
            }

            InetAddress multicastInetAddress = InetAddress.getByName(multicastAddress);
            InetSocketAddress group = new InetSocketAddress(multicastInetAddress, multicastPort);
            MulticastSocket socket = new MulticastSocket(null);

            // Set the network interface for the socket
            socket.setNetworkInterface(networkInterface);

            // Join the multicast group
            socket.joinGroup(group, networkInterface);

            byte[] buf = new byte[1024];

            System.out.println("Listening for advertisements...");

            System.out.println(socket.getInetAddress());
            System.out.println(socket.getNetworkInterface());

            while (true) {
                System.out.println("Waiting for multicast packets...");

                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                System.out.println("Received a multicast packet.");
                String message = new String(packet.getData(), 0, packet.getLength());
                System.out.println("Received advertisement: " + message);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
