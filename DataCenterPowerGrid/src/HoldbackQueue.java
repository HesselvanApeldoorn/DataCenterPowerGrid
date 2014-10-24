import java.util.List;
import java.util.LinkedList;
import java.util.PriorityQueue;
import java.util.Map;
import java.util.HashMap;


// TODO find a better name
class HoldbackQueue {
    /* This is a class that reorders the received messages in a FIFO
     * manner per peer. Thus, it doesn't guarantee causual or total
     * ordering, only per-peer FIFO ordering. Also note that multicast
     * ordering and peer-to-peer ordering must be kept separate! */
    private Map<Long, PriorityQueue<Middleware.ReceivedMessage>> messages;
    private Map<Long, Integer>                                  delivered;

    public HoldbackQueue() {
        this.messages  = new HashMap<Long, PriorityQueue<Middleware.ReceivedMessage>>(10);
        this.delivered = new DefaultHashMap<Long, Integer>(100, 0);
    }

    public synchronized void add(Middleware.ReceivedMessage message) {
        // have we already delivered it?
        if (message.payload.sequence_nr <= delivered.get(message.sender))
            return;
        if (!messages.containsKey(message.sender))
            messages.put(message.sender, new PriorityQueue<Middleware.ReceivedMessage>(10));
        messages.get(message.sender).offer(message);
    }

    public synchronized List<Middleware.ReceivedMessage> getDeliverableMessages(long sender) {
        List<Middleware.ReceivedMessage> deliverable = new LinkedList<Middleware.ReceivedMessage>();
        PriorityQueue<Middleware.ReceivedMessage> queue = messages.get(sender);
        if (queue == null)
            return deliverable; // which is empty
        while (queue.size() > 0) {
            // head is next message
            if (queue.peek().payload.sequence_nr == delivered.get(sender) + 1)
                deliverable.add(queue.poll()); // add to list, remove from queue
            else
                break; // no more messages to deliver
        }
        return deliverable;
    }
}
