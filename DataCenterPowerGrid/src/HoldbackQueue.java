import java.util.List;
import java.util.ListIterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;


// TODO find a better name
class HoldbackQueue {
    /* This is a class that reorders the received messages in a FIFO
     * manner per peer. Thus, it doesn't guarantee causual or total
     * ordering, only per-peer FIFO ordering. Also note that multicast
     * ordering and peer-to-peer ordering must be kept separate. */
    private LinkedList<Middleware.ReceivedMessage> queue;
    private Map<Long, Integer>                 delivered;
    public HoldbackQueue(Middleware theMiddleware) {
        this.queue     = new LinkedList<Middleware.ReceivedMessage>();
        this.delivered = new DefaultHashMap<Long, Integer>(100, 0);
    }

    public synchronized void give(Middleware.ReceivedMessage message) {
        ListIterator<Middleware.ReceivedMessage> iterator = queue.listIterator(0);
        while(iterator.hasNext()) {
            Middleware.ReceivedMessage current = iterator.next();
            if (current.sender == message.sender) {
                // insert the messages in order so that the algorithm below
                // to deliver them
                while (current.payload.sequence_nr < message.payload.sequence_nr &&
                       current.sender == message.sender &&
                       iterator.hasNext())
                    current = iterator.next();
                if (current.sender != message.sender)  // overshoot!
                    iterator.previous();
                break;
            }
        }
        iterator.add(message);
    }

    public synchronized List<Middleware.ReceivedMessage> getDeliverableMessages() {
        LinkedList<Middleware.ReceivedMessage> messages   = new LinkedList<Middleware.ReceivedMessage>();
        ListIterator<Middleware.ReceivedMessage> iterator = queue.listIterator(0);
        while (iterator.hasNext()) {
            Middleware.ReceivedMessage message = iterator.next();
            int lastDelivered = delivered.get(message.sender);
            if (lastDelivered + 1 == message.payload.sequence_nr) {
                messages.add(message);
                iterator.remove();
                delivered.put(message.sender, message.payload.sequence_nr);
            }
        }
        return messages;
    }
}