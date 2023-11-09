/*
  Application Configuration information
  CS4105 Practical P2 - Discover and Sahre

  Saleem Bhatti
  Oct 2023, Oct 2022, Oct 2021, Oct 2020, Sep 2019, Oct 2018

*/

/*
  This is an object that gets passed around, containing useful information.
*/

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
// https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Properties.html
import java.util.Properties;

public class Configuration {
  // Everything here "public" to allow tweaking from user code.
  public Properties properties;
  public String propertiesFile = "filetreebrowser.properties";
  public LogFileWriter log;
  public String logFile = "filetreebrowser.log";
  public int msgSize = 128; // bytes
  public int beacon = 10;
  // These default values can be overriden in the properties file.

  // 'id -u' gives a numeric uid, u, which will be unique within the lab.
  // You can construct your "personal" multicast address, by "splitting"
  // `u` across the lower 32 bits. For example, if `u` is 414243,
  // mAddr6 = ff02::41:4243
  // and your "personal" port number, mPort_ = u.
  public String hostName = "";
  public String mAddr4 = "ff02::4105:4105"; // CS4105 whole class group
  public String mAddr6 = "ff02::4105:4105"; // CS4105 whole class group
  public String nifName = "";
  public int mPort = 4105;
  public final String zeroAddr = "0"; // to indicate a "null" address
  public NetworkInterface nif = null;

  public int mTTL = 2; // plenty for the lab
  public boolean loopback = true; // ignore my own transmissions
  public boolean reuseAddr = false; // allow address use by other apps
  public int soTimeout = 1; // ms
  public int sleepTime = 5000; // ms

  // // // //
  // application config -- default values
  public String rootDir = "root_dir"; // sub-dir in current dir
  public String id; // System.getProperty("user.name") @ fqdn;
  public int maximumMessageSize = 500; // bytes
  public int maximumAdvertisementPeriod = 1000; // ms

  public Boolean checkOption(String value, String[] optionList) {
    boolean found = false;
    for (String option : optionList) {
      if (value.equals(option)) {
        found = true;
        break;
      }
    }
    return found;
  }

  public String[] true_false = { "true", "false" }; // Could have used enum.
  public String[] searchOptions = // Could have used enum.
      { "none", "path", "path-filename", "path-filename-substring" };
  public String search = "none"; // from searchOptions_
  public boolean download = false;

  public String hostInfo;

  Configuration(String propertiesFile) {
    if (propertiesFile != null) {
      this.propertiesFile = propertiesFile;
    }

    try {

      /*
       * This is *not* a general way of reliably discovering all the local IPv4
       * and IPv6 addresses being used by a host on all of its interfaces, and
       * working out which is the "main" interface. However, it works for the CS
       * lab linux machines, which have:
       * 1. a single gigabit ethernet interface.
       * 2. a single IPv4 address for that interface.
       * 3. only link-local IPv6 (no global IPv6 prefix).
       */
      InetAddress ip4 = InetAddress.getLocalHost(); // assumes IPv4!
      hostName = ip4.getHostName();
      mAddr4 = ip4.getHostAddress(); // assumes IPv4!
      nif = NetworkInterface.getByInetAddress(ip4); // assume the "main" interface
      nifName = nif.getName();
      Enumeration<InetAddress> e_addr = nif.getInetAddresses();
      while (e_addr.hasMoreElements()) {
        final InetAddress a = e_addr.nextElement();
        if (a.isLinkLocalAddress()) { // assume only this will be used
          // will include interface name, e.g. fe80:0:0:0:1067:14a1:4e8b:28ac%en0
          mAddr6 = a.getHostAddress();
          break;
        }
      }

      hostInfo = hostName + " " + nifName + " " + mAddr4 + " " + mAddr6;
      System.out.println("** detected: " + hostInfo);

      properties = new Properties();
      InputStream p = getClass().getClassLoader().getResourceAsStream(propertiesFile);
      if (p != null) {
        properties.load(p);
        String s;

        if ((s = properties.getProperty("logFile")) != null) {
          System.out.println(propertiesFile + " logFile: " + logFile + " -> " + s);
          logFile = new String(s);
        }

        if ((s = properties.getProperty("loopback")) != null) {
          System.out.println(propertiesFile + " loopback: " + loopback + " -> " + s);
          loopback = Boolean.valueOf(s);
        }

        if ((s = properties.getProperty("mAddr4")) != null) {
          System.out.println(propertiesFile + " mAddr4: " + mAddr4 + " -> " + s);
          mAddr4 = new String(s);
        }

        if ((s = properties.getProperty("mAddr6")) != null) {
          System.out.println(propertiesFile + " mAddr6: " + mAddr6 + " -> " + s);
          mAddr6 = new String(s);
        }

        if ((s = properties.getProperty("mPort")) != null) {
          System.out.println(propertiesFile + " mPort: " + mPort + " -> " + s);
          mPort = Integer.parseInt(s);
        }

        if ((s = properties.getProperty("mTTL")) != null) {
          System.out.println(propertiesFile + " mTTL: " + mTTL + " -> " + s);
          mTTL = Integer.parseInt(s);
        }

        if ((s = properties.getProperty("reuseAddr")) != null) {
          System.out.println(propertiesFile + " reuseAddr: " + reuseAddr + " -> " + s);
          reuseAddr = Boolean.valueOf(s);
        }

        if ((s = properties.getProperty("soTimeout")) != null) {
          System.out.println(propertiesFile + " soTimeout: " + soTimeout + " -> " + s);
          soTimeout = Integer.parseInt(s);
        }

        /*
         * Application-specific configuration
         */
        if ((s = properties.getProperty("msgSize")) != null) {
          System.out.println(propertiesFile + " msgSize: " + msgSize + " -> " + s);
          msgSize = Integer.parseInt(s);
        }

        if ((s = properties.getProperty("sleepTime")) != null) {
          System.out.println(propertiesFile + " sleepTime: " + sleepTime + " -> " + s);
          sleepTime = Integer.parseInt(s);
        }

        if ((s = properties.getProperty("beacon")) != null) {
          System.out.println(propertiesFile + " beacon: " + beacon + " -> " + s);
          beacon = Integer.parseInt(s);
        }

        p.close();
      }

      log = new LogFileWriter(logFile);
      log.writeLog("-* Detected: " + hostInfo, true);
      log.writeLog("-* logFile=" + logFile, true);
      log.writeLog("-* mAddr4=" + mAddr4, true);
      log.writeLog("-* mAddr6=" + mAddr6, true);
      log.writeLog("-* mPort=" + mPort, true);
      log.writeLog("-* mTTL=" + mTTL, true);
      log.writeLog("-* loopback=" + loopback, true);
      log.writeLog("-* reuseAddr=" + reuseAddr, true);
      log.writeLog("-* soTimeout=" + soTimeout, true);
      log.writeLog("-* msgSize=" + msgSize, true);
      log.writeLog("-* sleepTime=" + sleepTime, true);
      log.writeLog("-* beacon=" + beacon, true);
    }

    catch (UnknownHostException e) {
      System.out.println("Problem: " + e.getMessage());
    }

    catch (NumberFormatException e) {
      System.out.println("Problem: " + e.getMessage());
    }

    catch (IOException e) {
      System.out.println("Problem: " + e.getMessage());
    }

  }
}
