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
        this.pidCounter = 0;
    }

    @Override
    public synchronized void run() {
    	middleware.getMembership().setLeader(true);  // TODO: Not a good construction
        long now = System.currentTimeMillis();
        long then = now - 2 * HEARTBEAT_PERIOD;
        for (Map.Entry<Long,Long> ack: lastAcks.entrySet()) {
        	System.out.println("acks" + ack.getValue());
            if (ack.getValue() < then) {
        		System.out.println("dropping: " + ack.getKey());
                dropMember(ack.getKey());
            }
        }
        System.out.println("leader heartbeating");
        middleware.sendGroup(new HeartbeatMessage(now), false);
    }

    private void dropMember(long pid) {
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
