import java.util.TimerTask;
import java.util.Map;
import java.util.HashMap;

public class Leader extends TimerTask {
    public final static long HEARTBEAT_PERIOD = 5000;

    private Map<Long, Long> lastAcks;
    private Middleware middleware;
    private Group group;
    public Leader(Group theGroup, Middleware theMiddleware) {
        this.group = theGroup;
        this.middleware = theMiddleware;
        this.lastAcks = new HashMap<Long, Long>(100);
    }

    @Override
    public synchronized void run() {
        long now = System.currentTimeMillis();
        long then = now - 2 * HEARTBEAT_PERIOD;
        for (Map.Entry<Long,Long> ack: lastAcks.entrySet()) {
            if (ack.getValue() < then) {
                dropMember(ack.getKey());
            }
        }
        middleware.sendGroup(new HeartbeatMessage(now), false);
    }

    private void dropMember(long pid) {
        group.remove(pid);
        middleware.sendGroup(new LeaveMessage(group.getVersion(), pid), true);
    }

    public synchronized void receiveAcknowledge(Middleware.ReceivedMessage message) {
        lastAcks.put(message.sender, message.timestamp);
    }
}
