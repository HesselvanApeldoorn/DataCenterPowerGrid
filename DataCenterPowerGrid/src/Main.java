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
        DatagramSocket personalSocket  = getPersonalSocket();
        System.out.println(personalSocket.getLocalSocketAddress());
        MulticastSocket groupSocket    = new MulticastSocket(GROUP_PORT);
        groupSocket.joinGroup(groupAddress.getAddress());
        Middleware middleware          = new Middleware(personalSocket, groupSocket, groupAddress);
        middleware.start();
        if (argv.length > 0 && argv[0].equals("leader")) {
            Leader leader = new Leader(middleware.getGroup(), middleware);
            leader.run();
        }
        BlockingQueue<Middleware.ReceivedMessage> queue = middleware.getDeliveryQueue();
        for (int i = 0; i < 10; i++) {
        	Thread.sleep(1500);
        	middleware.sendGroup(new Message(i));
        	Middleware.ReceivedMessage message = queue.take();
        	System.out.printf("Received: %s from %s\n", message.message, message.packet.getSocketAddress());
        }
        middleware.shutdown();
        /* Is this necessary? we already closed the socket to stop the receiver */
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
