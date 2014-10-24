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
        if (list == null || list.size() == 0)
            return null;
        int  left = 0;
        int right = list.size();
        while (left < right) {
            int mid    = (left + right) / 2;
            int needle = list.get(mid).sequence_nr;
            if (needle > sequence_nr)
                left = mid + 1;
            else if (needle < sequence_nr)
                right = mid;
            else
                return list.get(mid);
        }
        return list.get(left).sequence_nr == sequence_nr ? list.get(left) : null;
    }

}
