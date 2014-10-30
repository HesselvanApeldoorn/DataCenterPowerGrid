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
import java.util.TimerTask;
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
    private Membership membership;

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

    private static class RetransmitRequest extends Message {
        public final long sender;
        public final int req_seq_nr;
        public final boolean req_is_mc;

        public RetransmitRequest(long sender, int req_seq_nr, boolean req_is_mc) {
            this.sender     = sender;
            this.req_seq_nr = req_seq_nr;
            this.req_is_mc  = req_is_mc;
        }
    }


    private static class RetransmitMessage extends Message {
        public final long sender;
        public final Message payload;
        public RetransmitMessage(long sender, Message payload) {
            this.sender = sender;
            this.payload = payload;
        }

        public ReceivedMessage unpack() {
            // hmm... not entirely happy here
            return new ReceivedMessage(System.currentTimeMillis(), sender, null, payload);
        }
    }

    private class Requester extends TimerTask {
        public void run() {
            for (HoldbackQueue.UndeliveredMessage r : peerQueue.getUndeliveredMessages()) {
                if (group.isAlive(r.sender))
                    send(r.sender, new RetransmitRequest(r.sender, r.sequence_nr, false), false); // unordered send
            }
            for (HoldbackQueue.UndeliveredMessage r : groupQueue.getUndeliveredMessages()) {
                if (group.isAlive(r.sender))
                    send(r.sender, new RetransmitRequest(r.sender, r.sequence_nr, true), false);
                else
                    sendGroup(new RetransmitRequest(r.sender, r.sequence_nr, true), false);
            }
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
        this.membership     = new Membership(group, this, true, peerSocket.getLocalPort());
    }

    private void prepareMessage(long receiver, Message message, boolean is_ordered) {
        message.is_multicast = (receiver == GROUP_PID);
        message.is_ordered   = is_ordered;
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

    public void send(long receiver, Message msg, boolean is_ordered) {
        prepareMessage(receiver, msg, is_ordered);
        SocketAddress address = this.group.getAddress(receiver);
        DatagramPacket packet = this.encodeMessage(address, msg);
        try {
            this.outputQueue.put(packet);
        } catch (InterruptedException ex) {
            System.out.println("Dropped message because of interrupt");
        }
    }

    public void sendGroup(Message msg, boolean is_ordered) {
        prepareMessage(GROUP_PID, msg, is_ordered);
        DatagramPacket packet = this.encodeMessage(groupAddress, msg);
        try {
            this.outputQueue.put(packet);
            this.sentMessages.add(GROUP_PID, msg);
        } catch (InterruptedException ex) {
            System.out.println("Dropped group message because of interrupt");
        }
    }

    public void resend(long requester, RetransmitRequest req) {
        if (req.is_multicast) {
            // if it was multicast, it means we're asking the group for delivered
            // messages of a now-dead group member
            Message message = deliveredMulticasts.find(req.sender, req.sequence_nr);
            if (message != null)
                sendGroup(new RetransmitMessage(req.sender, message), false);
        } else {
            Message message = sentMessages.find(req.req_is_mc ? GROUP_PID : requester, req.sequence_nr);
            if (message != null) {
                if (req.req_is_mc)
                    sendGroup(message, false);
                else
                    send(requester, message, false);
            }
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
                if (message.payload instanceof RetransmitRequest)
                    resend(message.sender, (RetransmitRequest)message.payload);
                else
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
        timer.schedule(new Requester(), 100l, 100l);
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
        if (message.payload instanceof RetransmitMessage) {
            // deliver the re-transmitted message
            deliverMessage(((RetransmitMessage)message.payload).unpack());
        } else if (message.payload.is_ordered) {
            if (message.sender == NO_PID)
                return; // don't deliver ordered messages from unknown
                        // senders, they could be delivered twice
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
    
    public Membership getMembership() {
    	return membership;
    }
}
