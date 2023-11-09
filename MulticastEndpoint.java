/**
 * IPv4/IPv6 multicast socket goop wrapper.

  Saleem Bhatti <saleem@st-andrews.ac.uk>
  Oct 2023, code check (sjm55).
  Sep 2022, updated code for dual-stack IPv4/IPv6.
  Sep 2021, updated code to remove deprecated API usage in Java 17 LTS.
  Sep 2020, code check.
  Sep 2019, code check.
  Oct 2018, initial version.

  This encapsulates the process of setting up an IPv4/IPv6 multicast
  communication endpoint, as well as sending and receiving from that
  endpoint.
*/

// Java 17 LTS from Sep 2021
// https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/net/MulticastSocket.html

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.StandardSocketOptions;

public class MulticastEndpoint {
    MulticastSocket mSocket;
    InetAddress mInetAddr4;
    InetAddress mInetAddr6;
    InetSocketAddress mGroup4;
    InetSocketAddress mGroup6;
    Configuration c;

    public enum PktType {
        none, ip4, ip6
    } // for dual stack flexibility

    /**
     * A multicast communication end-point, dual stack (IPv4 and IPv6).
     * It is possible to have sockets that are only either IPv4 or IPv6,
     * but this is an example of how to have both.
     * 
     * @param configuration : Configuration object, config info.
     */
    MulticastEndpoint(Configuration configuration) {
        c = configuration;

        try {

            mSocket = new MulticastSocket(c.mPort);
            mSocket.setOption(StandardSocketOptions.IP_MULTICAST_LOOP, c.loopback);
            mSocket.setReuseAddress(c.reuseAddr); // re-use of addr on this host
            mSocket.setTimeToLive(c.mTTL); // maximum number of hops to send
            mSocket.setSoTimeout(c.soTimeout); // non-blocking socket

            if (!c.mAddr4.equalsIgnoreCase(c.zeroAddr)) {
                mInetAddr4 = InetAddress.getByName(c.mAddr4);
                mGroup4 = new InetSocketAddress(mInetAddr4, c.mPort);
            }

            if (!c.mAddr6.equalsIgnoreCase(c.zeroAddr)) {
                mInetAddr6 = InetAddress.getByName(c.mAddr6);
                mGroup6 = new InetSocketAddress(mInetAddr6, c.mPort);
            }

            c.log.writeLog("using interface " + c.nif.getName());

        } // try

        catch (SocketException e) {
            System.out.println("MulticastEndpoint(): " + e.getMessage());
        }

        catch (IOException e) {
            System.out.println("MulticastEndpoint(): " + e.getMessage());
        }
    }

    /**
     * Join multicast group(s).
     */
    void join() {
        try {
            if (mGroup6 != null) {
                mSocket.joinGroup(mGroup6, c.nif);
                c.log.writeLog("joined IPv6 multicast group " + mGroup6.toString(), true);
            }
        } catch (IOException e) {
            System.out.println("MulticastEndpoint.join(): " + e.getMessage());
        }
    }

    /**
     * Leave multicast group(s).
     */
    void leave() {
        try {
            if (mGroup6 != null) {
                mSocket.leaveGroup(mGroup6, c.nif);
                c.log.writeLog("left IPv6 multicast group", true);
            }

            mSocket.close();

        } catch (IOException e) {
            System.out.println("MulticastEndpoint.leave(): " + e.getMessage());
        }
    }

    /**
     * Receive a multicast packet from a dual-stack socket.
     * 
     * @param b : byte array for received data
     * @return : type of packet received, IPv4 or IPv6
     */
    PktType rx(byte[] b) {
        PktType p = PktType.none;

        try {
            DatagramPacket d = new DatagramPacket(b, b.length);

            mSocket.receive(d);
            final int l = d.getLength();

            if (l > 0) {
                int addrLen = d.getAddress().getAddress().length;
                if (addrLen == 4)
                    p = PktType.ip4; // 4 bytes, IPv4
                if (addrLen == 16)
                    p = PktType.ip6; // 16 bytes, IPv6
            }
        } catch (SocketTimeoutException e) {
            // do nothing
        } catch (IOException e) {
            System.out.println("MulticastEndpoint.rx(): " + e.getMessage());
        }

        return p;
    }

    /**
     * @param p : select IPv4 or IPv6
     * @param b : bytes to send
     * @return true / false for successful / non-successful transmission
     */
    boolean tx(PktType p, byte b[]) {
        if (p == PktType.none)
            return false;

        boolean done;
        DatagramPacket d;

        done = false;
        try {
            d = new DatagramPacket(b, b.length, p == PktType.ip4 ? mGroup4 : mGroup6);
            mSocket.send(d);
            done = true;
        }

        catch (SocketTimeoutException e) {
            System.out.println("MulticastEndpoint.tx(): timeout on send - " + e.getMessage());
        } catch (SocketException e) {
            System.out.println("MulticastEndpoint.tx(): " + e.getMessage());
        } catch (IOException e) {
            System.out.println("MulticastEndpoint.tx(): " + e.getMessage());
        }

        return done;
    }

} // class MulticastEndpoint
