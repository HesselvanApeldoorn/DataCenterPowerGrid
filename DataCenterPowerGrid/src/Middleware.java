import java.net.DatagramPacket;
import java.net.SocketAddress;
import java.util.Timer;
import java.util.concurrent.BlockingQueue;


class Middleware extends Thread {
    private BlockingQueue<DatagramPacket> inputQueue;
    private BlockingQueue<DatagramPacket> outputQueue;
    private BlockingQueue<DatagramPacket> multicastOutputQueue;
    private BlockingQueue<Message>        deliveryQueue;
    private Group group;
    private Timer timer;
    private boolean stopped;

    public void Middleware(BlockingQueue<DatagramPacket> theInnputQueue,
                           BlockingQueue<DatagramPacket> theOutputQueue,
                           BlockingQueue<DatagramPacket> theMulticastQueue,
                           BlockingQue<Message> theDeliveryQueue,
                           Group theGroup) {
        this.inputQueue           = theInputQueue;
        this.outputQueue          = theOutputQueue;
        this.multicastOutputQueue = theMulicastQueue;
        this.deliveryQueue        = theDeliveryQueue;
        this.group                = theGroup;
        this.timer                = new Timer();
    }

    public void send(long pid, Message msg) {
        SocketAddress address = getSocketAddress(pid);
        DatagramPacket packet = encodeMessage(address, msg, false);
        try {
            this.outputQueue.put(packet);
        } catch (InterruptedException ex) {
            System.out.println("Dropped message because of interrupt");
        }
    }

    public void sendGroup(Message msg) {
        DatagramPacket packet = encodeMessage(address, null, true);
        try {
            this.multicastOutputQueue.put(packet);
        } catch (InterruptedException ex) {
            System.out.println("Dropped group message because of interrupt");
        }
    }

    public void run() {
        stopped = false;
        try {
            while (!stopped) {
                DatagramPacket packet = this.inputQueue.take();
                Message       message = decodeMessage(packet);
                // do reordering if necessary, no-op for now
                this.deliveryQueue.put(packet);
            }
        } catch (InterruptedException ex) {
            // guess somebody wanted us to stop
            stopped = true;
        }
        this.timer.cancel();
    }

    private SocketAddress getSocketAddress(long pid) {
        return groupMap.get(pid);
    }

    private Message decodeMessage(DatagramPacket packet) {
        long now = System.currentTimeMillis();
        return null;
    }

    private DatagramPacket encodeMessage(Message msg, SocketAddress addr,
                                         boolean isMulticast) {
        return null;
    }

    /* So that we may all use the same timer */
    public Timer getTimer() {
        return timer;
    }
}
