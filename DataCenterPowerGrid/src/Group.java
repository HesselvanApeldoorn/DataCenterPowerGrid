import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.net.SocketAddress;

/* A thread-safe mapping from PID's to SocketAddresses that represents
 * the group. */
class Group {
    private int nextPid;
    private Map<Integer, SocketAddress> pidToSocket;
    private Map<SocketAddress, Integer> socketToPid;

    public Group() {
        nextPid         = 0;
        pidToSocket     = new HashMap<Integer, SocketAddress>();
        socketToPid     = new DefaultHashMap<SocketAddress, Integer>(Middleware.NO_PID);
    }

    public synchronized void add(int pid, SocketAddress address) {
        pidToSocket.put(pid, address);
        socketToPid.put(address, pid);
        nextPid = (pid > nextPid ? pid : nextPid);
    }

    public synchronized void remove(int pid) {
        // TODO we should rather use tombstones
        // or even more rather, a log
        socketToPid.remove(pidToSocket.get(pid));
        pidToSocket.remove(pid);
    }

    public synchronized SocketAddress getAddress(int pid) {
        return pidToSocket.get(pid);
    }

    public synchronized int getPid(SocketAddress addr) {
        return socketToPid.get(addr);
    }

    public synchronized Set<Integer> getPids() {
        return pidToSocket.keySet();
    }

    public synchronized boolean isAlive(int pid) {
        return pidToSocket.containsKey(pid);
    }

    public synchronized int nextPid() {
        return ++nextPid;
    }

    public synchronized int maxPid() {
        return nextPid;
    }

    public synchronized int getSize() {
        return pidToSocket.size();
    }
}

