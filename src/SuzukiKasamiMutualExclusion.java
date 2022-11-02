import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Scanner;

@SuppressWarnings("BusyWait")
public class SuzukiKasamiMutualExclusion {

    public static final int CS_EXECUTE_TIME = 8000;
    public static final int WAITING_FOR_TOKEN_TIME = 2000;

    public static void main(String[] args) {

        // Read the config of nodes
        String sitesConfigFile = "SitesConfig.txt";

        try (BufferedReader reader = new BufferedReader(new FileReader(sitesConfigFile))) {
            int numberOfNodes = 0;
            String nodeAddress = reader.readLine();
            ArrayList<String> nodeList = new ArrayList<>();

            while (nodeAddress != null) {
                nodeList.add(nodeAddress);
                numberOfNodes++;
                nodeAddress = reader.readLine();
            }

            String[] ipAddress = new String[numberOfNodes];
            int[] port = new int[numberOfNodes];

            for (int counter = 0; counter < numberOfNodes; counter++) {
                String[] tmpAddress = nodeList.get(counter).split("\\|");
                ipAddress[counter] = tmpAddress[1];
                port[counter] = Integer.parseInt(tmpAddress[2]);
            }

            Scanner scanner = new Scanner(System.in);

            int thisSiteNumber;
            int isCorrectSiteNumber = 0;

            do {
                System.out.print("Enter site number (1-" + numberOfNodes + "): ");
                while (!scanner.hasNextInt()) {
                    System.out.println("That's not a number!");
                    scanner.next();
                }
                thisSiteNumber = Integer.parseInt(scanner.nextLine());
                if (thisSiteNumber >= 1 && thisSiteNumber <= numberOfNodes) {
                    isCorrectSiteNumber = 1;
                } else {
                    System.out.println("The site number you entered is out of range. Please enter the correct site number between 1 to  " + numberOfNodes);
                }
            } while (isCorrectSiteNumber == 0);

            boolean hasToken = thisSiteNumber == 1;

            Site localSite = new Site(numberOfNodes, thisSiteNumber, hasToken, ipAddress, port);

            // Open a socket for current site
            ListenToBroadcast listenToBroadcast = new ListenToBroadcast(localSite, port[thisSiteNumber - 1]);
            listenToBroadcast.start();
            String inputQuery = "";
            while (!inputQuery.equalsIgnoreCase("quit")) {
                System.out.println("Press ENTER to enter CS: ");
                Scanner scan_query = new Scanner(System.in);
                inputQuery = scan_query.nextLine();
                System.out.println("Site-" + thisSiteNumber + " is trying to enter Critical Section");
                if (localSite.hasToken) {
                    localSite.processingCS = true;
                    System.out.println("Site-" + thisSiteNumber + " has token. Executing in the Critical Section.....");
                } else {
                    System.out.println("Site-" + thisSiteNumber + " doesn't have token. So Site-" + thisSiteNumber + " is requesting token");
                    localSite.requestCriticalSection();
                    System.out.println("Site-" + thisSiteNumber + " is waiting for token.");
                    localSite.processingCS = true;
                    while (!localSite.hasToken) {
                        Thread.sleep(WAITING_FOR_TOKEN_TIME);
                    }
                    System.out.println("Site-" + thisSiteNumber + " has received token. Executing in Critical Section.....");
                }
                Thread.sleep(CS_EXECUTE_TIME);
                localSite.processingCS = false;
                System.out.println("Site-" + thisSiteNumber + " is exiting Critical Section.");
                System.out.println();
                exitCS(localSite, thisSiteNumber, numberOfNodes, ipAddress, port, numberOfNodes);
            }

        } catch (Exception e) {
            System.out.println("SuzukiKasamiMutualExclusion - main - Exception");
            e.printStackTrace();
        }
    }

    public static void exitCS(Site localSite, int thisSiteNumber, int numberOfNodes, String[] ipAddress, int[] port, int numOfSites) {
        localSite.LN[thisSiteNumber - 1] = localSite.RN[thisSiteNumber - 1];
        // Send updated LN value to all sites
        String message = "ln," + thisSiteNumber + "," + localSite.LN[thisSiteNumber - 1];

        for (int i = 0; i < numOfSites; i++) {
            if (i == thisSiteNumber - 1) {
                continue;
            }

            try {
                Socket socket = new Socket(ipAddress[i], port[i]);
                OutputStream outputStream = socket.getOutputStream();
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
                BufferedWriter bufferedWriter = new BufferedWriter(outputStreamWriter);
                bufferedWriter.write(message);
                bufferedWriter.flush();
                socket.close();
            } catch (UnknownHostException e) {
                System.out.println("SuzukiKasamiMutualExclusion - exitCS - UnknownHostException");
                e.printStackTrace();
            } catch (IOException e) {
                System.out.println("SuzukiKasamiMutualExclusion - exitCS - IOException");
                e.printStackTrace();
            }
        }

        for (int i = 0; i < numberOfNodes; i++) {
            if (localSite.RN[i] == localSite.LN[i] + 1) {
                if (!localSite.tokenQueue.contains(i + 1)) {
                    localSite.tokenQueue.add(i + 1);
                }
            }
        }

        if (localSite.tokenQueue.size() > 0) {
            localSite.sendToken(localSite.tokenQueue.poll());
        }
    }
}

class Site {
    String[] ipAddress;
    int[] port;

    int numberOfSites;
    int siteNumber;
    boolean hasToken;
    boolean processingCS = false;
    Queue<Integer> tokenQueue = new LinkedList<>();
    int[] RN;
    int[] LN;

    Site(int numberOfSites, int siteNumber, boolean hasToken, String[] ipAddress, int[] portNo) {
        this.numberOfSites = numberOfSites;
        this.siteNumber = siteNumber;
        this.hasToken = hasToken;

        this.ipAddress = ipAddress;
        this.port = portNo;

        RN = new int[this.numberOfSites];
        LN = new int[this.numberOfSites];
        for (int i = 0; i < numberOfSites; i++) {
            RN[i] = 0;
            LN[i] = 0;
        }
    }

    void updateLN(int thisSite, int value) {
        LN[thisSite - 1] = value;
    }

    void requestCriticalSection() {
        RN[siteNumber - 1]++;
        String message = "request," + siteNumber + "," + RN[siteNumber - 1];
        System.out.println("Broadcasting request to other " + (numberOfSites - 1) + " sites : ");

        for (int i = 0; i < numberOfSites; i++) {
            if (i != siteNumber - 1) {
                Socket socket = null;

                try {
                    socket = new Socket(ipAddress[i], port[i]);
                    System.out.println(i + ". Broadcasting to the site with port :" + socket.getPort());
                    OutputStream outputStream = socket.getOutputStream();
                    OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
                    BufferedWriter bufferedWriter = new BufferedWriter(outputStreamWriter);
                    bufferedWriter.write(message);
                    bufferedWriter.flush();
                    outputStream.close();
                    outputStreamWriter.close();
                    bufferedWriter.close();

                } catch (UnknownHostException e) {
                    System.out.println("Site - requestCriticalSection - UnknownHostException");
                    e.printStackTrace();
                } catch (IOException e) {
                    System.out.println("Site - requestCriticalSection - IOException");
                    e.printStackTrace();
                } finally {
                    try {
                        assert socket != null;
                        socket.close();
                    } catch (IOException e) {
                        System.out.println("Site - requestCriticalSection - IOException while closing socket");
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    void processCriticalSectionReq(int site, int sn) {
        if (RN[site - 1] < sn) {
            RN[site - 1] = sn;
        }
        if (!processingCS && hasToken) {
            sendToken(site);
        } else {
            tokenQueue.add(site);
        }
    }

    void sendToken(int site) {
        if (this.hasToken) {
            if (RN[site - 1] == LN[site - 1] + 1) {
                System.out.println("Sending token to site " + site);
                try {
                    Socket socket = new Socket(ipAddress[site - 1], port[site - 1]);
                    StringBuilder message = new StringBuilder("token");
                    int tokenQueueLen = tokenQueue.size();
                    for (int i = 0; i < tokenQueueLen; i++) {
                        message.append(",").append(tokenQueue.poll());
                    }

                    OutputStream outputStream = socket.getOutputStream();
                    OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
                    BufferedWriter bufferedWriter = new BufferedWriter(outputStreamWriter);
                    bufferedWriter.write(message.toString());
                    bufferedWriter.flush();
                    socket.close();
                    this.hasToken = false;
                } catch (UnknownHostException e) {
                    System.out.println("Site - sendToken - UnknownHostException");
                    e.printStackTrace();
                } catch (IOException e) {
                    System.out.println("Site - sendToken - IOException");
                    e.printStackTrace();
                }
            }
        }
    }
}

@SuppressWarnings({"resource", "InfiniteLoopStatement"})
class ListenToBroadcast extends Thread {

    int port;
    Site localSite;

    public ListenToBroadcast(Site thisSite, int port) {
        this.port = port;
        this.localSite = thisSite;
    }

    public void run() {
        try {
            ServerSocket serverSocket = new ServerSocket(port);
            while (true) {
                Socket socket = serverSocket.accept();
                new ProcessRequest(socket, localSite).start();
            }
        } catch (Exception e) {
            System.out.println("ListenToBroadcast - run - Exception");
            e.printStackTrace();
        }
    }
}

class ProcessRequest extends Thread {

    Socket socket;
    Site localSite;

    public ProcessRequest(Socket socket, Site site) {
        this.socket = socket;
        this.localSite = site;
    }

    public void run() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
            String command;
            String[] message;
            command = reader.readLine();
            System.out.println("ProcessRequest - command is - " + command);
            if (command != null) {
                if (command.charAt(0) == 'r') {
                    message = command.split(",");
                    System.out.println("Site-" + Integer.parseInt(message[1]) + " has requested for Critical Section");
                    localSite.processCriticalSectionReq(Integer.parseInt(message[1]), Integer.parseInt(message[2]));
                }
                if (command.charAt(0) == 't') {
                    message = command.split(",");
                    localSite.tokenQueue.clear();
                    int length = message.length;
                    for (int i = 1; i < length; i++) {
                        localSite.tokenQueue.add(Integer.parseInt(message[i]));
                    }
                    localSite.hasToken = true;
                }
                if (command.charAt(0) == 'l') {
                    message = command.split(",");
                    System.out.println("Site-" + Integer.parseInt(message[1]) + " has left the Critical Section");
                    localSite.updateLN(Integer.parseInt(message[1]), Integer.parseInt(message[2]));
                    System.out.println();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                socket.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
