import java.io.IOException;
import java.net.MulticastSocket;
import java.net.DatagramSocket;
import java.net.BindException;
import java.net.SocketException;
import java.net.SocketAddress;
import java.net.InetSocketAddress;


public class Main {
    public static final String GROUP_HOSTNAME = "224.0.0.224";
    public static final int    GROUP_PORT     = 8437;
	
    public static void main(String[] argv) throws IOException {
        InetSocketAddress groupAddress = new InetSocketAddress(GROUP_HOSTNAME, GROUP_PORT);
        DatagramSocket personalSocket  = getPersonalSocket();
        MulticastSocket groupSocket    = new MulticastSocket(GROUP_PORT);
        groupSocket.joinGroup(groupAddress.getAddress());
        Middleware middleware         = new Middleware(personalSocket, groupSocket, groupAddress);
        middleware.start();
        middleware.shutdown();
        groupSocket.leaveGroup(groupAddress.getAddress());
    }


    private static DatagramSocket getPersonalSocket() throws SocketException {
        for (int i = 0; i < 100; i++) {
            try {
                return new DatagramSocket(8000 + i);
            } catch (BindException e) {
                continue;
            }
        }
        return null;
    }
}
