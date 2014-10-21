import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.net.SocketAddress;

/* A thread-safe versioned mapping from PID's to SocketAddresses that
 * represents the group. */
class Group {
    private long pid;
    private long version;
    private Map<Long, SocketAddress> pidToSocket;
    private Map<SocketAddress, Long> socketToPid;

    public Group() {
        version         = 0;
        pidToSocket     = new HashMap<Long, SocketAddress>();
        socketToPid     = new HashMap<SocketAddress, Long>();
    }

    public synchronized void add(long pid, SocketAddress address) {
        pidToSocket.put(pid, address);
        socketToPid.put(address, pid);
        version += 1;
    }

    public synchronized void remove(long pid) {
        socketToPid.remove(pidToSocket.get(pid));
        pidToSocket.remove(pid);
        version += 1;
    }

    public synchronized SocketAddress getAddress(long pid) {
        return pidToSocket.get(pid);
    }

    public synchronized long getPid(SocketAddress addr) {
        return socketToPid.get(addr);
    }

    public synchronized long getVersion() {
        return version;
    }

    public synchronized Set<Long> getPids() {
        return pidToSocket.keySet();
    }
}

