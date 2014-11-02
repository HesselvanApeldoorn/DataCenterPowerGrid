import java.util.TimerTask;
import java.util.Map;
import java.util.HashMap;
import java.net.SocketAddress;

public class Leader extends TimerTask {
    public final static long HEARTBEAT_PERIOD = 5000;

    private Map<Long, Long> lifeSigns;
    private Middleware middleware;
    private Group group;
    public final int  term;

    public Leader(Group theGroup, Middleware theMiddleware, int term) {
        this.group      = theGroup;
        this.middleware = theMiddleware;
        this.lifeSigns  = new HashMap<Long, Long>(100);
        this.term = term;
    }

    public static class Heartbeat extends Message {
        public final long timestamp;
        public final int  term;
        public Heartbeat(long timestamp, int term) {
            this.timestamp = timestamp;
            this.term      = term;
        }
    }

    public static class Welcome extends Message {
        public final long pid;
        public Welcome(long pid) {
            this.pid = pid;
        }
    }

    @Override
    public synchronized void run() {
        long now  = System.currentTimeMillis();
        long then = now - 3 * HEARTBEAT_PERIOD;
        for (Map.Entry<Long,Long> item: lifeSigns.entrySet()) {
            if (item.getValue() < then) {
                long pid = item.getKey();
                group.remove(pid);
                middleware.sendGroup(new Member.Leave(group.getVersion(), pid), true);
           }
        }
        middleware.sendGroup(new Heartbeat(now, term), false);
    }

    public synchronized void onAlive(long sender, SocketAddress address, long timestamp) {
        if (sender == Middleware.NO_PID) {
            sender = group.nextPid();
            group.add(sender, address);
            middleware.send(sender, new Welcome(sender), true);
            middleware.sendGroup(new Member.Join(group.getVersion(), sender, address), true);
        }
        lifeSigns.put(sender, timestamp);
    }

}
