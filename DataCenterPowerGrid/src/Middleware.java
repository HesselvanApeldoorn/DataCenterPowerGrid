import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.MulticastSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.util.Timer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

class Middleware extends Thread {
    public final static long GROUP_PID = 0;
    public final static long    NO_PID = -1;

    private DatagramSocket  peerSocket;
    private Receiver        peerReceiver;
    private MulticastSocket groupSocket;
    private Receiver        groupReceiver;
    private Sender          sender;

    private SocketAddress groupAddress;

    private BlockingQueue<DatagramPacket>  inputQueue;
    private BlockingQueue<DatagramPacket>  outputQueue;
    private BlockingQueue<ReceivedMessage> deliveryQueue;

    private HoldbackQueue peerQueue;
    private HoldbackQueue groupQueue;
    private ResendBuffer  resendBuffer;
    private Group group;

    private Timer timer;
    private boolean stopped;

    public static class ReceivedMessage {
        public final long timestamp;
        public final long sender;
        public final DatagramPacket packet;
        public final Message payload;
        public ReceivedMessage(long theTimestamp, long thePid,
                               DatagramPacket thePacket, Message theMessage) {
            this.timestamp = theTimestamp;
            this.sender    = thePid;
            this.packet    = thePacket;
            this.payload   = theMessage;
        }
    }

    public Middleware(DatagramSocket thePeerSocket, MulticastSocket theGroupSocket, SocketAddress theGroupAddress) {
        this.peerSocket     = thePeerSocket;
        this.groupSocket    = theGroupSocket;
        this.groupAddress   = theGroupAddress;
        this.inputQueue     = new LinkedBlockingQueue<DatagramPacket>();
        this.outputQueue    = new ArrayBlockingQueue<DatagramPacket>(10);
        this.deliveryQueue  = new ArrayBlockingQueue<ReceivedMessage>(10);
        this.peerReceiver   = new Receiver(inputQueue, peerSocket);
        this.groupReceiver  = new Receiver(inputQueue, groupSocket);
        this.sender         = new Sender(outputQueue, peerSocket);
        this.timer          = new Timer();
        this.group          = new Group();
        this.resendBuffer   = new ResendBuffer();

        this.peerQueue      = new HoldbackQueue(this);
        this.groupQueue     = new HoldbackQueue(this);
    }

    public void send(long receiver_pid, Message msg) {
        SocketAddress address = this.group.getAddress(receiver_pid);
        msg.is_multicast = false;
        DatagramPacket packet = this.encodeMessage(address, msg);
        try {
            this.outputQueue.put(packet);
            this.resendBuffer.addSentMessage(receiver_pid, msg);
        } catch (InterruptedException ex) {
            System.out.println("Dropped message because of interrupt");
        }
    }

    public void sendGroup(Message msg) {
        msg.is_multicast = true;
        DatagramPacket packet = this.encodeMessage(groupAddress, msg);
        try {
            this.outputQueue.put(packet);
            this.resendBuffer.addSentMessage(GROUP_PID, msg);
        } catch (InterruptedException ex) {
            System.out.println("Dropped group message because of interrupt");
        }
    }

    public void run() {
        stopped = false;
        this.setup();
        try {
            while (!stopped) {
                DatagramPacket packet = this.inputQueue.take();
                ReceivedMessage message = this.decodeMessage(packet);
                if (message == null) {
                    System.err.println("Received undecodable message");
                    continue;
                }
                deliverMessage(message);
            }
        } catch (InterruptedException ex) {
            // guess somebody wanted us to stop
            stopped = true;
        }
        this.teardown();
    }

    public void shutdown() {
        stopped = true;
        interrupt();
    }

    private void setup() {
        sender.start();
        groupReceiver.start();
        peerReceiver.start();
    }

    private void teardown() {
        timer.cancel();
        groupSocket.close();
        peerSocket.close();
        reallyJoin(sender);
        reallyJoin(groupReceiver);
        reallyJoin(peerReceiver);
    }

    private void reallyJoin(Thread thread) {
        while (thread.isAlive()) {
            try {
                thread.interrupt();
                thread.join();
            } catch (InterruptedException e) {
                continue;
            }
        }
    }

    private ReceivedMessage decodeMessage(DatagramPacket packet) {
        long now = System.currentTimeMillis();
        try {
            ObjectInputStream stream = new ObjectInputStream(
                    new ByteArrayInputStream(packet.getData(), packet.getOffset(), packet.getLength())
            );
            Message message = (Message) stream.readObject();
            long    sender = group.getPid(packet.getSocketAddress());
            return new ReceivedMessage(now, sender, packet, message);
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (ClassCastException e) {
            e.printStackTrace();
        }
        return null;
    }

    private DatagramPacket encodeMessage(SocketAddress addr,
                                         Message message) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(8192);
            ObjectOutputStream stream    = new ObjectOutputStream(buffer);
            stream.writeObject(message);
            stream.close();
            return new DatagramPacket(buffer.toByteArray(), buffer.size(), addr);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void deliverMessage(ReceivedMessage message) throws InterruptedException {
        if (message.payload.is_ordered) {
            if (message.payload.is_multicast) {
               this.groupQueue.give(message);
                for (ReceivedMessage deliverable : this.groupQueue.getDeliverableMessages()) {
                    this.deliveryQueue.put(message);
                    this.resendBuffer.addDeliveredMulticast(message.sender, message.payload);
                }
           } else {
                this.peerQueue.give(message);
                for (ReceivedMessage deliverable : this.peerQueue.getDeliverableMessages()) {
                    this.deliveryQueue.put(deliverable);
                }
            }
        } else {
            this.deliveryQueue.put(message);
        }
    }

    /* So that we may all use the same timer */
    public Timer getTimer() {
        return timer;
    }

    public BlockingQueue<ReceivedMessage> getDeliveryQueue() {
        return deliveryQueue;
    }

    public Group getGroup() {
        return group;
    }
}
