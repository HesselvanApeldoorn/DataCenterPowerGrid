import java.util.TimerTask;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.net.SocketAddress;

public class Leader extends TimerTask {
    public final static long HEARTBEAT_PERIOD = 5000;

    private Map<Long, Long> lifeSigns;
    private Middleware middleware;
    private Group group;
    public final int  term;
    public final long pid;

    public Leader(Group theGroup, Middleware theMiddleware, long pid, int term) {
        this.group      = theGroup;
        this.middleware = theMiddleware;
        this.lifeSigns  = new HashMap<Long, Long>(100);
        this.pid  = pid;
        this.term = term;
    }

    public static class Heartbeat extends Message {
        public final long timestamp;
        public final long pid;
        public final int  term;
        public Heartbeat(long timestamp, long pid, int term) {
            this.timestamp = timestamp;
            this.pid       = pid;
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
        System.err.println("Leader.run()");
        long now  = System.currentTimeMillis();
        long then = now - 3 * HEARTBEAT_PERIOD;
        List<Long> dropped = new ArrayList<Long>(10);
        for (Map.Entry<Long,Long> item: lifeSigns.entrySet()) {
            if (item.getValue() < then) {
                long pid = item.getKey();
                group.remove(pid);
                dropped.add(pid);
                System.err.printf("Leader.drop(%d)\n", pid);
                middleware.sendGroup(new Member.Leave(pid), true);
           }
        }
        for (Long pid : dropped)
            lifeSigns.remove(pid);
        middleware.sendGroup(new Heartbeat(now, pid, term), false);
    }

    public synchronized void onAlive(long sender, SocketAddress address, long timestamp) {
        System.err.printf("onAlive(%d, %s, %d)\n", sender, address.toString(), timestamp);
        if (sender == Middleware.NO_PID) {
            sender = group.nextPid();
            group.add(sender, address);
            middleware.send(sender, new Welcome(sender), true);
            middleware.sendGroup(new Member.Join(sender, address), true);
        }
        lifeSigns.put(sender, timestamp);
    }

}
