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

    /* Ensure reliability */
    private HoldbackQueue peerQueue;
    private HoldbackQueue groupQueue;
    private ResendBuffer  sentMessages;
    private ResendBuffer  deliveredMulticasts;
    private Sequencer     sequencer;

    private Group group;

    private Timer timer;
    private boolean stopped;

    public static class ReceivedMessage implements Comparable<ReceivedMessage> {
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

        public int compareTo(ReceivedMessage msg) {
            if (msg.sender != this.sender)
                throw new IllegalArgumentException("Can't compare messages from different senders");
            return this.payload.sequence_nr - msg.payload.sequence_nr;
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
        this.sentMessages   = new ResendBuffer();
        this.deliveredMulticasts = new ResendBuffer();
        this.peerQueue      = new HoldbackQueue();
        this.groupQueue     = new HoldbackQueue();
        this.sequencer      = new Sequencer();
    }

    private void prepareMessage(long receiver, Message message) {
        message.is_multicast = (receiver == GROUP_PID);
        if (message.is_ordered) {
            // theoretically, we can be raced between getting the
            // sequence number and inserting it into the resend
            // buffer. so, lock here to ensure that this doesn't
            // happen
            synchronized (this) {
                message.sequence_nr = sequencer.next(receiver);
                this.sentMessages.add(receiver, message);
            }
        }
    }

    public void send(long receiver, Message msg) {
        prepareMessage(receiver, msg);
        SocketAddress address = this.group.getAddress(receiver);
        DatagramPacket packet = this.encodeMessage(address, msg);
        try {
            this.outputQueue.put(packet);
        } catch (InterruptedException ex) {
            System.out.println("Dropped message because of interrupt");
        }
    }

    public void sendGroup(Message msg) {
        prepareMessage(GROUP_PID, msg);
        DatagramPacket packet = this.encodeMessage(groupAddress, msg);
        try {
            this.outputQueue.put(packet);
            this.sentMessages.add(GROUP_PID, msg);
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
               this.groupQueue.add(message);
               // I don't think we can be raced here. Because there is
               // only a single caller, namely our own thread :-)

               // The HoldbackQueue gives us the messages in delivery
               // order, so both the delivery queue and the
               // ResendBuffer will get them in delivery order too
               for (ReceivedMessage deliverable : this.groupQueue.getDeliverableMessages(message.sender)) {
                   this.deliveryQueue.put(message);
                   this.deliveredMulticasts.add(message.sender, message.payload);
               }
           } else {
                this.peerQueue.add(message);
                for (ReceivedMessage deliverable : this.peerQueue.getDeliverableMessages(message.sender)) {
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

    /* NB: changing the group makes no sense - all classes
     * depend on some stability of pid's! */
    public Group getGroup() {
        return group;
    }
}
