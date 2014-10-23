import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

public class ResendBuffer {
    public Map<Long, List<Message>> sentMessages;
    public Map<Long, List<Message>> deliveredMulticasts;

    public ResendBuffer() {
        sentMessages        = new HashMap<Long, List<Message>>(10);
        deliveredMulticasts = new HashMap<Long, List<Message>>(10);
    }

    public synchronized void addSentMessage(long receiver, Message message) {
        // messages are added in sequence order
        if (!sentMessages.containsKey(receiver)) {
            sentMessages.put(receiver, new ArrayList<Message>(10));
        }
        sentMessages.get(receiver).add(message);
    }

    public synchronized void addDeliveredMulticast(long sender, Message message) {
        if (!deliveredMulticasts.containsKey(sender)) {
            deliveredMulticasts.put(sender, new ArrayList<Message>(10));
        }
        deliveredMulticasts.get(sender).add(message);
    }

    public synchronized Message findSentMessage(long pid, int sequence_nr) {
        return findInList(sentMessages.get(pid), sequence_nr);
    }

    public synchronized Message findDeliveredMulticast(long pid, int sequence_nr) {
        return findInList(deliveredMulticasts.get(pid), sequence_nr);
    }

    private Message findInList(List<Message> list, int sequence_nr) {
        int  left = 0;
        int right = list.size();
        int   mid = (left + right) / 2;
        while (mid > left) {
            int needle = list.get(mid).sequence_nr;
            if (needle > sequence_nr) {
                right = mid;
                mid   = (left + right) / 2;
            } else if (needle < sequence_nr) {
                left = mid;
                mid  = (left + right) / 2;
            } else {
                return list.get(mid);
            }
        }
        if (mid == right)
            return null;
        if (list.get(mid).sequence_nr == sequence_nr)
            return list.get(mid);
        return null;
    }

}
