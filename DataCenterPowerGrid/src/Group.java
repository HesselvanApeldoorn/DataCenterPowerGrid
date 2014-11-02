import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.net.SocketAddress;

/* A thread-safe mapping from PID's to SocketAddresses that represents
 * the group. */
class Group {
    private int version;
    private long nextPid;
    private Map<Long, SocketAddress> pidToSocket;
    private Map<SocketAddress, Long> socketToPid;

    public Group() {
        version         = 0;
        nextPid         = 0l;
        pidToSocket     = new HashMap<Long, SocketAddress>();
        socketToPid     = new DefaultHashMap<SocketAddress, Long>(Middleware.NO_PID);
    }

    public synchronized void add(long pid, SocketAddress address) {
        pidToSocket.put(pid, address);
        socketToPid.put(address, pid);
        version += 1;
        nextPid = (pid > nextPid ? pid : nextPid);
    }

    public synchronized void remove(long pid) {
        // TODO we should rather use tombstones
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

    public synchronized int getVersion() {
        return version;
    }

    public synchronized boolean isAlive(long pid) {
        return pidToSocket.containsKey(pid);
    }

    public synchronized long nextPid() {
        return ++nextPid;
    }


    public synchronized void setLeaderAddress(SocketAddress address) {
        add(Middleware.LEADER_PID, address);
    }

    public synchronized SocketAddress getLeaderAddress() {
        return pidToSocket.get(Middleware.LEADER_PID);
    }
}

