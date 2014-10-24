import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

public class ResendBuffer {
    public Map<Long, List<Message>> messageLists;

    public ResendBuffer() {
        messageLists = new HashMap<Long, List<Message>>(10);
    }

    public synchronized void add(long pid, Message message) {
        // messages are added in sequence order
        if (!messageLists.containsKey(pid)) {
            messageLists.put(pid, new ArrayList<Message>(10));
        }
        messageLists.get(pid).add(message);
    }

    public synchronized Message find(long pid, int sequence_nr) {
        List<Message> list = messageLists.get(pid);
        if (list == null)
            return null;
        int first = list.get(0).sequence_nr;
        int last = first + list.size() - 1;
        if (first > sequence_nr || last < sequence_nr)
            return null;
        return list.get(sequence_nr - first);
    }

}
