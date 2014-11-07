import java.util.TimerTask;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.net.SocketAddress;

public class Leader extends TimerTask {
    public final static long HEARTBEAT_PERIOD = 5000;

    private Map<Integer, Long> lifeSigns;
    private Middleware middleware;
    private Group group;
    private int runCount;
    public final int term;

    public Leader(Group theGroup, Middleware theMiddleware, int term) {
        this.group      = theGroup;
        this.middleware = theMiddleware;
        this.lifeSigns  = new DefaultHashMap<Integer, Long>(100, -1l);
        this.runCount   = 0;
        this.term       = term;
    }

    public static class Heartbeat extends Message {
        public final long timestamp;
        public final int pid;
        public final int term;
        public final int state[];
        public Heartbeat(long timestamp, int pid, int term, int state[]) {
            this.timestamp = timestamp;
            this.pid       = pid;
            this.term      = term;
            this.state     = state;
        }
    }

    public static class Welcome extends Message {
        public final int pid;
        public Welcome(int pid) {
            this.pid = pid;
        }
    }

    @Override
    public synchronized void run() {
        System.err.println("Leader.run()");
        long now  = System.currentTimeMillis();
        long then = now - 3 * HEARTBEAT_PERIOD;
        if (runCount > 2) {
            List<Integer> dropped = new ArrayList<Integer>(10);
            for (Integer hostPid : group.getPids()) {
                if (lifeSigns.get(hostPid) < then) {
                    dropped.add(hostPid);
                    System.err.printf("Leader.drop(%d)\n", hostPid);
                    middleware.sendGroup(new Member.Leave(hostPid), true);
                }
            }
            for (Integer dropPid : dropped) {
                group.remove(dropPid);
                lifeSigns.remove(dropPid);
            }
        }
        runCount++;
        sendHeartbeat();
    }

    public synchronized void onAlive(int sender, SocketAddress address, long timestamp) {
      //  System.err.printf("onAlive(%d, %s, %d)\n", sender, address.toString(), timestamp);
        if (sender == Middleware.NO_PID) {
            sender = group.nextPid();
            group.add(sender, address);
            middleware.send(sender, new Welcome(sender), true);
            middleware.sendGroup(new Member.Join(sender, address), true);
        }
        lifeSigns.put(sender, timestamp);
    }

    public void sendHeartbeat() {
        Heartbeat heartbeat = new Heartbeat(System.currentTimeMillis(), middleware.getPid(), term,
                                            middleware.getMessageState());
        middleware.sendGroup(heartbeat, false);
    }

}
