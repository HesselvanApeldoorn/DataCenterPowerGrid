import java.io.IOException;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.util.concurrent.BlockingQueue;

/* Receives UDP messages and puts them on a queue. Works on
 * MulticastSockets as well (they are a subclass) */
class Receiver extends Thread {
    private BlockingQueue<Message> queue;
    private DatagramSocket         socket;

    public Receiver(BlockingQueue<Message> aQueue, 
                    DatagramSocket aSocket) {
        this.queue   = aQueue;
        this.socket  = aSocket;
    }

    public void run() {
        while(!this.socket.isClosed()) {
            try {
                byte         buf[] = new byte[8192];
                DatagramPacket pkt = new DatagramPacket(buf, buf.length);
                this.socket.receive(pkt);
                this.queue.put(new Message(System.currentTimeMillis(),
                                           pkt.getData(), pkt));
            } catch (InterruptedException e) {
                /* We could not put it on the queue. This means the package
                 * is dropped. However, we continue to listen */
                continue;
            } catch (IOException e) {
                if (socket.isClosed())
                    return;
                e.printStackTrace();
            }
        }
    }

    public void close() {
        /* close the socket */
        this.socket.close();
    }
}

