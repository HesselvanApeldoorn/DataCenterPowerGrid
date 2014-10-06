import java.io.IOException;
import java.net.DatagramSocket;
import java.net.DatagramPacket;
import java.util.concurrent.BlockingQueue;

/* Receives UDP messages and puts them on a queue. Works on
 * MulticastSocket as well (extends DatagramSocket) */
class Receiver extends Thread {
    private BlockingQueue<DatagramPacket> queue;
    private DatagramSocket                socket;

    public Receiver(BlockingQueue<DatagramPacket> aQueue,
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
                /* Timestamping now happens in the middleware, causing
                   a variable-time delay. But that is ok. */
                this.queue.put(pkt);
            } catch (InterruptedException e) {
                /* We could not put it on the queue. This means the package
                 * is dropped. However, we continue to listen */
                continue;
            } catch (IOException e) {
                if (socket.isClosed())
                    break;
                e.printStackTrace();
            }
        }
        /* Cleanup? Never heard of that */
    }

    public void close() {
        /* close the socket */
        this.socket.close();
    }
}

