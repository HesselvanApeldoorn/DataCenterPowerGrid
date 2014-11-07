import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;

class Sender extends Thread {
    private BlockingQueue<DatagramPacket> queue;
    private DatagramSocket               socket;

    public Sender(BlockingQueue<DatagramPacket> aQueue,
                  DatagramSocket aSocket) {
        this.queue  = aQueue;
        this.socket = aSocket;
    }

    public void run() {
        System.err.println("Sender start");
        while (!socket.isClosed()) {
            try {
                DatagramPacket packet = queue.take();
                if (Math.random() < 0.95)
                	socket.send(packet);
            } catch (IOException ex) {
                System.out.println("Package was dropped because of IO exception");
                ex.printStackTrace();
                if (socket.isClosed())
                    break;
            } catch (InterruptedException ex) {
                continue;
            }
        }
        System.err.println("Sender stop");
        /* No cleanup here, either */
    }
}

