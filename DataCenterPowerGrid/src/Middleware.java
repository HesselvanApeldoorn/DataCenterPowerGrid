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
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.TreeSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

class Middleware extends Thread {
    public final static int  GROUP_PID = 0;
    public final static int     NO_PID = -1;

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
    private Resender      resender;

    private Group group;

    private Timer timer;

    private int myPid = NO_PID;
    private boolean stopped; // TODO - make this depend on socket / group connected status

    public static class ReceivedMessage {
        public final long timestamp;
        public final int sender;
        public final DatagramPacket packet;
        public final Message payload;
        public ReceivedMessage(long theTimestamp, int theSender,
                               DatagramPacket thePacket, Message theMessage) {
            this.timestamp = theTimestamp;
            this.sender    = theSender;
            this.packet    = thePacket;
            this.payload   = theMessage;
        }
    }

    private static class RetransmitRequest extends Message {
        public final int sender;
        public final int req_seq_nr;

        public RetransmitRequest(int sender, int req_seq_nr) {
            this.sender     = sender;
            this.req_seq_nr = req_seq_nr;
        }
    }


    private static class RetransmitMessage extends Message {
        public final int sender;
        public final Message payload;
        public RetransmitMessage(int sender, Message payload) {
            this.sender = sender;
            this.payload = payload;
        }

        public ReceivedMessage unpack() {
            // hmm... not entirely happy here
            return new ReceivedMessage(System.currentTimeMillis(), sender, null, payload);
        }
    }

    private class Requester extends TimerTask {
        /* periodically request undelivered group messages */
        public void run() {
            for (HoldbackQueue.UndeliveredMessage r : groupQueue.getUndeliveredMessages()) {
                request(r.sender, r.sequence_nr);
            }
        }
    }

    private class Resender extends TimerTask {
        /* resend unacknowledged peer-to-peer messages periodically */
        private Map<Integer, Set<Integer>> unacknowledged;
        public Resender() {
            unacknowledged = new HashMap<Integer, Set<Integer>>();
        }

        public synchronized void run() {
            /* TODO - this does not take congestion into account */
            for (Map.Entry<Integer, Set<Integer>> item : unacknowledged.entrySet()) {
                int receiver = item.getKey();
                if (!group.isAlive(receiver))
                    continue;
                for (Integer sequence_nr : item.getValue())
                    resend(myPid, receiver, sequence_nr);
            }
        }

        public synchronized void add(int receiver, int sequence_nr) {
            if (!unacknowledged.containsKey(receiver))
                unacknowledged.put(receiver, new HashSet<Integer>());
            unacknowledged.get(receiver).add(sequence_nr);
        }

        public synchronized void acknowledge(int receiver, int sequence_nr) {
            if (unacknowledged.containsKey(receiver))
                unacknowledged.get(receiver).remove(sequence_nr);
        }
    }

    private static class Acknowledge extends Message {
        public final int ack_seq_nr;
        public Acknowledge(int ack_seq_nr) {
            this.ack_seq_nr = ack_seq_nr;
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
        this.resender       = new Resender();
    }

    private void prepareMessage(int receiver, Message message, boolean is_ordered) {
        message.is_multicast = (receiver == GROUP_PID);
        message.is_ordered   = is_ordered;
        if (message.is_ordered) {
            // theoretically, we can be raced between getting the
            // sequence number and inserting it into the resend
            // buffer. so, lock here to ensure that this doesn't
            // happen
            synchronized (this) {
                message.sequence_nr = sequencer.next(receiver);
                System.err.printf("Send message #%d to %d\n", message.sequence_nr, receiver);
                this.sentMessages.add(receiver, message);
            }
            if (!message.is_multicast) {
                this.resender.add(receiver, message.sequence_nr);
            }
        }
    }

    public void send(int receiver, Message msg, boolean is_ordered) {
        prepareMessage(receiver, msg, is_ordered);
        send(this.group.getAddress(receiver), msg);
    }

    public void sendGroup(Message msg, boolean is_ordered) {
        prepareMessage(GROUP_PID, msg, is_ordered);
        send(groupAddress, msg); // group address should just be gotten from the map
    }

    public void send(SocketAddress addr, Message msg) {
        DatagramPacket packet = this.encodeMessage(addr, msg);
        try {
            this.outputQueue.put(packet);
        } catch (InterruptedException ex) {
            System.out.println("Dropped group message because of interrupt");
        }
    }

    public void resend(int sender, int receiver, int sequence_nr) {
        Message message = (sender == myPid ?
                           sentMessages.find(receiver, sequence_nr) :
                           deliveredMulticasts.find(sender, sequence_nr));
        if (message != null) {
            if (sender != myPid)
                message = new RetransmitMessage(sender, message);
            if (receiver == GROUP_PID) {
                send(groupAddress,message);
            } else {
                send(group.getAddress(receiver), message);
            }
        }
    }

    public void request(int sender, int sequence_nr) {
        RetransmitRequest req = new RetransmitRequest(sender, sequence_nr);
        if (group.isAlive(sender))
            send(sender, req, false);
        else
            sendGroup(req, false);
    }

    public int[] getMessageState() {
        int state[] = new int[group.maxPid()]; // allocate an array for all nodes
        for (int i = 0; i < state.length; i++) {
            if (i == myPid) {
                state[i] = sentMessages.getLastTo(GROUP_PID);
            } else {
                state[i] = groupQueue.getLastOf(i);
            }
        }
        return state;
    }

    public void compareMessageState(int state[]) {
        for (int i = 0; i < state.length; i++) {
            if (i == myPid && sentMessages.getLastTo(GROUP_PID) > state[i]) {
                resend(myPid, GROUP_PID, state[i] + 1);
            } else if (!group.isAlive(i) && groupQueue.getLastOf(i) > state[i]) {
                resend(i, GROUP_PID, state[i] + 1);
            } else if (groupQueue.getLastOf(i) < state[i]) {
                request(i, state[i]);
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
                if (message.payload instanceof RetransmitRequest) {
                    RetransmitRequest req = (RetransmitRequest)message.payload;
                    resend(req.sender, GROUP_PID, req.req_seq_nr);
                } else {
                    deliverMessage(message);
                }
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
        timer.schedule(resender, 125l, 100l);
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
            int      sender = group.getPid(packet.getSocketAddress());
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
            if (message.sender != NO_PID)
                deliverMessage(((RetransmitMessage)message.payload).unpack());
        } else if(message.payload instanceof Acknowledge) {
            // we acknowledged a message
            resender.acknowledge(message.sender, ((Acknowledge)message.payload).ack_seq_nr);
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
                   System.err.printf("Delivering multicast message %d from %d\n", deliverable.payload.sequence_nr, deliverable.sender);
                   this.deliveryQueue.put(deliverable);
                   this.deliveredMulticasts.add(deliverable.sender, deliverable.payload);
               }
           } else {
                this.send(message.sender, new Acknowledge(message.payload.sequence_nr), false);
                this.peerQueue.add(message);
                for (ReceivedMessage deliverable : this.peerQueue.getDeliverableMessages(message.sender)) {
                    System.err.printf("Delivering message %d from %d\n", deliverable.payload.sequence_nr,
                                      deliverable.sender);
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

    public int getPid() {
        return myPid;
    }

    public void setPid(int pid) {
        this.myPid = pid;
    }

}
