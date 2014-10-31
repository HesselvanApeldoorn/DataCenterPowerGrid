import java.util.TimerTask;
import java.util.Map;
import java.util.HashMap;

public class Leader extends TimerTask {
    public final static long HEARTBEAT_PERIOD = 5000;

    private Map<Long, Long> lastAcks;
	private Middleware middleware;
    private Group group;

	private long pidCounter;
    public Leader(Group theGroup, Middleware theMiddleware) {
        this.group = theGroup;
        this.middleware = theMiddleware;
        this.lastAcks = new HashMap<Long, Long>(100);
        long now = System.currentTimeMillis();
        long max = -1;
        for(long pid : group.getPids()) {// makes sure all processes are checked on activity by leader
        	lastAcks.put(pid, now);
        	if (pid > max) 
        		max = pid;
        }
        this.pidCounter = max + 1;
    }

    @Override
    public synchronized void run() {
    	Map<Long, Long> acksToRemove = new HashMap<Long, Long>(100);
    	middleware.getMembership().setLeader(true);  // TODO: Not a good construction
        long now = System.currentTimeMillis();
        long then = now - 2 * HEARTBEAT_PERIOD;
        for (Map.Entry<Long,Long> ack: lastAcks.entrySet()) {
            if (ack.getValue() < then) {
        		System.out.println("dropping: " + ack.getKey());
                dropMember(ack.getKey());
                acksToRemove.put(ack.getKey(), ack.getValue());
            }
        }
        for(Map.Entry<Long, Long> ack: acksToRemove.entrySet())
            lastAcks.remove(ack.getKey());
        middleware.sendGroup(new HeartbeatMessage(now), false);
    }

    private synchronized void dropMember(long pid) {
        group.remove(pid);
        middleware.sendGroup(new LeaveMessage(group.getVersion(), pid), true);
    }

    public synchronized void receiveAcknowledge(Middleware.ReceivedMessage message) {
        lastAcks.put(message.sender, message.timestamp);
    }
    
    public synchronized void handoutPid(Middleware.ReceivedMessage receivedMessage) {
        JoinMessage message = (JoinMessage) receivedMessage.payload;
        this.group.add(pidCounter, receivedMessage.packet.getSocketAddress());
        middleware.sendGroup(new AckJoinMessage(pidCounter++, receivedMessage.packet.getSocketAddress(), group), false);
    }
}
