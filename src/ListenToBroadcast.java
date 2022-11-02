import java.net.ServerSocket;
import java.net.Socket;

@SuppressWarnings({"InfiniteLoopStatement", "resource"})
public class ListenToBroadcast extends Thread {

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
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
}
