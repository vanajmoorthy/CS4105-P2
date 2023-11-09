
/**
 * Browses a file tree with a simple text based output and navigation.
 *
 * @author   <a href="https://saleem.host.cs.st-andrews.ac.uk/">Saleem Bhatti</a>
 * @version  1.4, 06 October 2022
 */

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public final class FileTreeBrowser {

  // user commands
  final static String quit = new String(":quit");
  final static String help = new String(":help");
  final static String services = new String(":services");
  final static String up = new String("..");
  final static String list = new String(".");
  final static String nodes = new String(":nodes");
  final static String search = new String(":search");
  final static String download = new String(":download");

  final static String propertiesFile = "filetreebrowser.properties";
  static Configuration configuration;
  static MulticastEndpoint m;
  static String username = System.getProperty("user.name");
  final static int txRatio = 5;
  static String rootPath = "";
  private static Map<String, Node> discoveredNodes = new HashMap<>();

  static long serialNumber = 1;

  File thisDir; // this directory
  String thisDirName; // name of this directory
  SimpleDateFormat sdf;

  private static ScheduledExecutorService scheduler;

  private static final int BEACON_INTERVAL = 5;

  public static String timestamp() {
    SimpleDateFormat sdf = new SimpleDateFormat(new String("yyyyMMdd-HHmmss.SSS"));
    return sdf.format(new Date());
  }

  /**
   * @param args : no args required
   */
  public static void main(String[] args) {

    configuration = new Configuration(propertiesFile);

    m = new MulticastEndpoint(configuration);

    m.join();

    rootPath = getPathName(new File(configuration.rootDir));

    InputStream keyboard = System.in;
    String userCmd = new String(list);
    boolean quitBrowser = false;

    FileTreeBrowser ftb = new FileTreeBrowser(configuration.rootDir);
    ftb.printList();

    scheduler = Executors.newScheduledThreadPool(1);

    startListeners();

    while (!quitBrowser) {

      System.out.print("\n[filename | '" + list + "' | '" + up + "' | '" + services + "' | '" + nodes + "' | '" + search
          + "' | '" + download + "' | '" + quit + "' | '" + help + "'] ");

      // what does the user want to do?
      while ((userCmd = ByteReader.readLine(keyboard)) == null) {
        try {
          Thread.sleep(configuration.sleepTime);
        } catch (InterruptedException e) {
        } // Thread.sleep() - do nothing
      }

      // blank
      if (userCmd.isBlank()) {
        continue;
      }

      // quit
      if (userCmd.equalsIgnoreCase(quit)) {
        quitBrowser = true;
      }

      // help message
      else if (userCmd.equalsIgnoreCase(help)) {
        displayHelp();
      }

      // service info
      else if (userCmd.equalsIgnoreCase(services)) {
        displayServices();
      }

      // list files
      else if (userCmd.equalsIgnoreCase(list)) {
        ftb.printList();
      }

      // move up directory tree
      else if (userCmd.equalsIgnoreCase(up)) {
        // move up to parent directory ...
        // but not above the point where we started!
        if (ftb.thisDirName.equals(rootPath)) {
          System.out.println("At root : cannot move up.\n");
        } else {
          String parent = ftb.thisDir.getParent();
          System.out.println("<<< " + parent + "\n");
          ftb = new FileTreeBrowser(parent);
        }
      }

      // list discovered servers
      else if (userCmd.equalsIgnoreCase(nodes)) {
        nodes();
      }

      else if (userCmd.equalsIgnoreCase(search)) {
        search();
      }

      // download
      else if (userCmd.equalsIgnoreCase(download)) {
        download();
      }

      else { // do something with pathname

        File f = ftb.searchList(userCmd);

        if (f == null) {
          System.out.println("Unknown command or filename: '" + userCmd + "'");
        }

        // act upon entered filename
        else {

          String pathName = getPathName(f);

          if (f.isFile()) { // print some file details
            System.out.println("file: " + pathName);
            System.out.println("size: " + f.length());
          }

          else if (f.isDirectory()) { // move into to the directory
            System.out.println(">>> " + pathName);
            ftb = new FileTreeBrowser(pathName);
          }

        } // (f == null)

      } // do something

    } // while(!quit)

    stopListeners();
  } // main()

  /**
   * Create a new FileTreeBrowser.
   *
   * @param pathName the pathname (directory) at which to start.
   */
  public FileTreeBrowser(String pathName) {
    if (pathName == "") {
      pathName = configuration.rootDir;
    } else // "." -- this directory, re-list only
    if (pathName.equals(list)) {
      pathName = thisDirName;
    }
    thisDir = new File(pathName);
    thisDirName = getPathName(thisDir);

    scheduler = Executors.newScheduledThreadPool(1);
    startBeacon();
  }

  private void startBeacon() {
    final Runnable beeper = new Runnable() {
      public void run() {
        txBeacon();
      }
    };
    scheduler.scheduleAtFixedRate(beeper, 0, BEACON_INTERVAL, TimeUnit.SECONDS);
  }

  public void stopBeacon() {
    scheduler.shutdown();
    try {
      if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
        scheduler.shutdownNow();
      }
    } catch (InterruptedException e) {
      scheduler.shutdownNow();
    }
  }

  private static void startListeners() {
    startBeaconListener();
    startSearchListeners();
  }

  private static void startBeaconListener() {
    scheduler.scheduleAtFixedRate(
        new Runnable() {
          @Override
          public void run() {
            rxBeacon();
          }
        }, 0, 4, TimeUnit.SECONDS); // adjust the interval as needed
  }

  private static void startSearchListeners() {
    scheduler.scheduleAtFixedRate(
        new Runnable() {
          @Override
          public void run() {
            // Handle both search requests and search responses
            byte[] buffer = new byte[configuration.msgSize];
            MulticastEndpoint.PktType pktType = m.rx(buffer);

            if (pktType != MulticastEndpoint.PktType.none) {
              String message = new String(buffer, StandardCharsets.US_ASCII).trim();

              if (message.contains(":search-request:")) {
                handleSearchRequest(message);
              } else if (message.contains(":search-result:") || message.contains(":search-error:")) {
                handleSearchResponse(message);
              }
            }
          }
        }, 0, 1, TimeUnit.SECONDS); // Polling interval of 1 second for demo purposes
  }

  private static void stopListeners() {
    stopBeaconListener();
    stopSearchListeners();
  }

  private static void stopBeaconListener() {
    scheduler.shutdown();
    try {
      if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
        scheduler.shutdownNow();
      }
    } catch (InterruptedException e) {
      scheduler.shutdownNow();
    }
  }

  private static void stopSearchListeners() {
    if (scheduler != null && !scheduler.isShutdown()) {
      scheduler.shutdown();
      try {
        if (!scheduler.awaitTermination(1, TimeUnit.SECONDS)) {
          scheduler.shutdownNow();
        }
      } catch (InterruptedException e) {
        scheduler.shutdownNow();
      }
    }
  }

  /**
   * Print help message.
   */
  static void displayHelp() {

    String[] lines = {
        "--* Welcome to the simple FileTreeBrowser. *--",
        "* The display consists of:",
        "\t- The name of the current directory",
        "\t- The list of files (the numbers for the files are of no",
        "\t  significance, but may help you with debugging).",
        "* Files that are directories have trailing '" + File.separator + "'.",
        "* Use text entry to navigate the directory tree.",
        "\t.\t\tTo refresh the view of the current directory.",
        "\t..\t\tTo move up a directory level.",
        "\tfilename\tTo list file details (if it is a file) or to",
        "\t\t\tmove into that directory (if it is a directory name).",
        "\t:services\tTo list the services offered.",
        "\t:nodes\t\tTo list the other nodes discovered.",
        "\t:download\tTo download a file.",
        "\t:quit\t\tTo quit the program.",
        "\t:help\t\tTo print this message."
    };

    for (int i = 0; i < lines.length; ++i)
      System.out.println(lines[i]);

    return;
  }

  /**
   * Print config information.
   */
  static void displayServices() {

    String services = ":";
    services += "id=" + configuration.id + ":";
    services += "timestamp=" + timestamp() + ":";
    services += "search=" + configuration.search + ",";
    services += "download=" + configuration.download;
    services += ":";

    System.out.println(services);
  }

  static void nodes() {
    // Print out the discovered nodes
    for (Node node : discoveredNodes.values()) {
      System.out.println("---NODE DETAILS---");
      node.printDetails();
      System.out.println("------------------");
    }
  }

  static void search() {
    Scanner scanner = new Scanner(System.in);
    System.out.print("\n * search: Please enter the filename you want to search for: ");
    String searchTerm = scanner.nextLine();

    if (searchTerm == null || searchTerm.isBlank()) {
      System.out.println("Search term cannot be empty.");
      return;
    }

    // Now you have the search term, and you can proceed with searching for it.
    System.out.println("Searching for: " + searchTerm);

    // You can create a search message and broadcast it here
    String searchMessage = createSearchMessage(searchTerm);
    broadcastSearchMessage(searchMessage);

    // scanner.close(); //
  }

  static String createSearchMessage(String searchTerm) {
    try {
      String identifier = username + "@" + InetAddress.getLocalHost().getCanonicalHostName();

      // Get the current timestamp
      String timestamp = timestamp();

      // Construct the header
      String header = identifier + ":" + serialNumber++ + ":" + timestamp;

      String payload = ":" + header + ":search-request:filename:" + searchTerm;

      return payload;
    } catch (UnknownHostException e) {
      e.printStackTrace();
    }
    return null;
  }

  static void broadcastSearchMessage(String message) {
    try {
      byte[] messageBytes = message.getBytes("US-ASCII");
      m.tx(MulticastEndpoint.PktType.ip6, messageBytes);
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
  }

  static void handleSearchRequest(String request) {
    String[] requestParts = request.split(":");

    if (requestParts.length < 6) {
      System.out.println("Invalid search request format.");
      return;
    }

    String filenameToSearch = requestParts[6];
    // Start searching for the file
    File rootDir = new File(configuration.rootDir);
    List<File> matchingFiles = new ArrayList<>();
    searchForFile(rootDir, filenameToSearch, matchingFiles);

    // Print the search results
    if (!matchingFiles.isEmpty()) {
      System.out.println("Search results for '" + filenameToSearch + "':");
      for (File matchingFile : matchingFiles) {
        System.out.println(getSubPath(matchingFile.getAbsolutePath()));
        sendSearchResponse(true, getSubPath(matchingFile.getAbsolutePath()), request);
      }
    } else {
      System.out.println("No matching files found for '" + filenameToSearch + "'.");
      sendSearchResponse(false, filenameToSearch, request);
    }
  }

  static void sendSearchResponse(boolean wasFileFound, String path, String request) {
    try {
      String[] requestParts = request.split(":");

      String identifier = requestParts[1];
      String myIdentifier = username + "@" + InetAddress.getLocalHost().getCanonicalHostName();

      int requestSerialNumber = Integer.parseInt(requestParts[2]);

      if (wasFileFound) {
        String response = ":" + myIdentifier + ":" + serialNumber++ + ":" + "search-result:" + identifier + ":"
            + requestSerialNumber
            + ":" + path + ":";
        // Send the search result response using your MulticastEndpoint instance
        sendMulticastMessage(response);
      } else {
        String response = ":" + myIdentifier + ":" + serialNumber++ + ":" + "search-error:" + identifier + ":"
            + requestSerialNumber
            + ":";

        sendMulticastMessage(response);
      }
    } catch (UnknownHostException e) {
      e.printStackTrace();
    }
  }

  static void handleSearchResponse(String response) {
    System.out.println("Received search response: " + response);
    // Parse the response and handle it as needed
    // Extract relevant information from the response message
    // and take appropriate action, e.g., displaying search results.
  }

  static String getSubPath(String fullPath) {
    String rootDirPath = "root_dir" + File.separator;

    // Check if the fullPath contains rootDirPath
    if (fullPath.contains(rootDirPath)) {
      // Get the index after the rootDirPath
      int startIndex = fullPath.indexOf(rootDirPath) + rootDirPath.length();
      String relativePath = fullPath.substring(startIndex);

      return relativePath;
    } else {
      return "";
    }
  }

  static void sendMulticastMessage(String message) {
    try {
      byte[] messageBytes = message.getBytes("US-ASCII");
      m.tx(MulticastEndpoint.PktType.ip6, messageBytes);
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
  }

  static void searchForFile(File directory, String filenameToSearch, List<File> matchingFiles) {
    File[] files = directory.listFiles();

    if (files == null) {
      return; // Directory is empty or an I/O error occurred
    }

    for (File file : files) {
      if (file.isDirectory()) {
        // Recursively search in subdirectories
        searchForFile(file, filenameToSearch, matchingFiles);
      } else {
        // Check if the filename contains the search term as a substring
        String currentFilename = file.getName();
        if (currentFilename.contains(filenameToSearch)) {
          matchingFiles.add(file);
        }
      }
    }
  }

  static void download() { // TBC
    System.out.println("\n * download: TBC");
  }

  /**
   * List the names of all the files in this directory.
   */
  public void printList() {

    File[] fileList = thisDir.listFiles();

    System.out.println("\n+++  id: " + configuration.id);
    System.out.println("+++ dir: " + getPathName(thisDir));
    System.out.println("+++\tfilename:");
    for (int i = 0; i < fileList.length; ++i) {

      File f = fileList[i];
      String name = f.getName();
      if (f.isDirectory()) // add a trailing separator to dir names
        name = name + File.separator;
      System.out.println(i + "\t" + name);
    }
    System.out.println("+++");
  }

  String getParent() {
    return thisDir.getParent();
  }

  /**
   * Search for a name in the list of files in this directory.
   *
   * @param name the name of the file to search for.
   */
  public File searchList(String name) {

    File found = null;

    File[] fileList = thisDir.listFiles();
    for (int i = 0; i < fileList.length; ++i) {

      if (name.equals(fileList[i].getName())) {
        found = fileList[i];
        break;
      }
    }

    return found;
  }

  /**
   * Get full pathname.
   *
   * @param f the File for which the pathname is required.
   */
  static public String getPathName(File f) {

    String pathName = null;

    try {
      pathName = f.getCanonicalPath();
    } catch (IOException e) {
      System.out.println("+++ FileTreeBrowser.pathname(): " + e.getMessage());
    }

    return pathName;
  }

  static void rxBeacon() {

    try {
      byte[] b = new byte[configuration.msgSize];
      MulticastEndpoint.PktType p = m.rx(b);

      if (p == MulticastEndpoint.PktType.none)
        return;

      String receivedMessage = new String(b, "US-ASCII").trim();
      // Parse the received beacon message here, assuming it follows a certain format
      String[] beaconParts = receivedMessage.split(":");
      if (beaconParts.length > 2) {
        String identifier = beaconParts[1]; // Adjust indices based on your message format
        String timestamp = beaconParts[3];
        String services = beaconParts[beaconParts.length - 1]; // Assuming services is the last part

        Node node = discoveredNodes.get(identifier);
        if (node == null) {
          node = new Node(identifier, new SimpleDateFormat("yyyyMMdd-HHmmss.SSS").parse(timestamp), services);
          discoveredNodes.put(identifier, node);
        } else {
          // Update the timestamp and possibly other details of the existing node
          node.setTimestamp(new SimpleDateFormat("yyyyMMdd-HHmmss.SSS").parse(timestamp));
        }
      }
    } catch (UnsupportedEncodingException e) {
      System.out.println("rxBeacon(): " + e.getMessage());
    } catch (ParseException e) {
      System.out.println("Error parsing the timestamp: " + e.getMessage());
    }
  }

  /**
   * Transmit a beacon.
   */
  static void txBeacon() {
    try {
      String identifier = username + "@" + InetAddress.getLocalHost().getCanonicalHostName();

      // Get the current timestamp
      String timestamp = timestamp();

      // Construct the header
      String header = identifier + ":" + serialNumber++ + ":" + timestamp;

      // Assume serverPort is defined somewhere in your configuration
      int serverPort = configuration.mPort; // Replace with actual port

      // Assume servicesString is a properly formatted string of services offered
      String servicesString = "search=none"; // Replace with actual services status

      // Construct the payload
      String payload = "advertisement:" + serverPort + ":" + servicesString;

      // Construct the complete message
      String message = ":" + header + ":" + payload + ":";

      byte[] b = null;

      try {

        b = message.getBytes("US-ASCII");
        if (m.tx(MulticastEndpoint.PktType.ip6, b))
          configuration.log.writeLog("tx6-> " + message, true);

        // :saleem@my.host1.net:528491:20231013-174242.042:advertisement:10123:search=none:

      }

      catch (UnsupportedEncodingException e) {
        System.out.println("txBeacon(): " + e.getMessage());
      }
    } catch (UnknownHostException e) {
      System.err.println("Cannot determine the local host name.");
      e.printStackTrace();
    }

  }
}
