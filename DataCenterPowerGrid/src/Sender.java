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
        while (!socket.isClosed()) {
            try {
                DatagramPacket packet = queue.take();
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
        /* No cleanup here, either */
    }
}

