import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.net.SocketAddress;

/* A thread-safe versioned mapping from PID's to SocketAddresses that
 * represents the group. */
class Group {
    private long pid;
    private long version;
    private Map<Long, SocketAddress> map;

    public Group() {
        version = 0;
        map     = new HashMap<Long, SocketAddress>();
    }

    public synchronized void add(long pid, SocketAddress address) {
        map.put(pid, address);
        version += 1;
    }

    public synchronized void remove(long pid) {
        map.remove(pid);
        version += 1;
    }

    public synchronized SocketAddress getAddress(long pid) {
        return map.get(pid);
    }

    public synchronized long getVersion() {
        return version;
    }

    public synchronized Set<Long> getPids() {
        return map.keySet();
    }
    
    public long getPid(SocketAddress addr) {
    	return -1;
    }
}

