import java.io.IOException;
import java.net.MulticastSocket;
import java.net.DatagramSocket;
import java.net.BindException;
import java.net.SocketException;
import java.net.SocketAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;

public class Main {
    public static final String GROUP_HOSTNAME = "224.0.0.224";
    public static final int    GROUP_PORT     = 8437;

    public static void main(String[] argv) throws IOException, InterruptedException {
        InetSocketAddress groupAddress = new InetSocketAddress(GROUP_HOSTNAME, GROUP_PORT);
        DatagramSocket peerSocket  = getPeerSocket();
        MulticastSocket groupSocket    = new MulticastSocket(GROUP_PORT);
        groupSocket.joinGroup(groupAddress.getAddress());
        Middleware middleware          = new Middleware(peerSocket, groupSocket, groupAddress);
        middleware.start();
        BlockingQueue<Middleware.ReceivedMessage> queue = middleware.getDeliveryQueue();
        for (int i = 0; i < 10; i++) {
            Thread.sleep(1500);
            middleware.sendGroup(new Message());
            Middleware.ReceivedMessage message = queue.take();
            System.out.printf("Received: %s from %d\n", message.payload, message.sender);
            if (message.sender == -1) {
                middleware.getGroup().add(1, message.packet.getSocketAddress());
            }
        }
        middleware.shutdown();
    }


    private static DatagramSocket getPeerSocket() throws SocketException {
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
