import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;
// TODO find a better name
class HoldbackQueue {
    private LinkedList<Middleware.ReceivedMessage> holdbackQueue;;
    private Map<Long, Integer> delivered;
    public HoldbackQueue(Middleware theMiddleware) {
        this.holdbackQueue = new LinkedList<Middleware.ReceivedMessage>();
        this.delivered     = new DefaultHashMap<Long, Integer>(100, 0);
    }

    public synchronized void give(ReceivedMessage message) {
        ListIterator<Middleware.ReceivedMessage> iterator = holdbackQueue.listIterator(0);
        while(iterator.hasNext()) {
            ReceivedMessage current = iterator.next();
            if (current.sender == message.sender) {
                while (current.sequence_nr < message.payload.sequence_nr && 
                       current.sender == message.sender &&
                       iterator.hasNext())
                    current = iterator.next();
                if (current.sender != message.sender) 
                    iterator.previous(); // step back - note that this must exist otherwise we 
                break;
            }
        }
        iterator.add(message);
    }

    public synchronized List<Middleware.ReceivedMessage> getDeliverableMessages() {
        LinkedList<Middleware.ReceivedMessage> messages = new LinkedList<ReceivedMessage>();
        ListIterator<Midlleware.ReceivedMessage> iterator = holdbackQueue.listIterator(0);
        while (iterator.hasNext()) {
            ReceivedMessage message = iterator.next();
            int lastDelivered = delivered.get(message.sender);
            if (lastDeliverd + 1 == message.payload.sequence_nr) {
                messages.add(message);
                iterator.remove();
                delivered.put(message.sender, message.payload.sequence_nr);
            }
        }
        return messages;
    }
}
