import java.net.SocketAddress;
import java.util.TimerTask;
import java.util.Timer;


class Member {
    public final static long HEARTBEAT_PERIOD = 5000;

    private Group   group;
    private Middleware middleware;
    private boolean isLeader = false;
    private boolean canLead = false;
    private boolean inElection = false;
    private long    pid;
    private long    lastHeartbeat;

    public Member(Group theGroup, Middleware theMiddleware, boolean iCanLead) {
        this.group      = theGroup;
        this.middleware = theMiddleware;
        this.canLead    = iCanLead;
    }

    public class Election extends TimerTask {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            if (lastHeartbeat < (now - 2 * HEARTBEAT_PERIOD)) {
                
            }
        }
    }

    public static class Alive extends Message {
        // nothing to do here, move along
    }

    public static class JoinRequest extends Message {
        // ehm, same as alive request?
    }

   public static class Leave extends Message {
        public final int version;
        public final long member;
        public Leave(int version, long member) {
            this.version = version;
            this.member  = member;
        }
    }

    public static class Join extends Message {
        /* Announce the join request */
        public final int version;
        public final long member;
        public final SocketAddress address;
        public Join(int version, long member, SocketAddress address) {
            this.version = version;
            this.member  = member;
            this.address = address;
        }
    }

    public void onHeartbeat(long sender, long timestamp, Leader.Heartbeat message) {
        // in theory, compute the difference between our received timestamp and
        // the leaders timestamp. In practice, just reply saying you're alive
        lastHeartbeat = System.currentTimeMillis();
        middleware.send(sender, new Alive(), false);
    }

    public void onJoin(Join request) {
        if (group.getVersion() + 1 == request.version) {
            group.add(request.member, request.address);
        } else {
            // hmm it's likely this request comes from a different leader.
        }
    }

    public void onLeave(Leave request) {
        if (group.getVersion() + 1 == request.version) {
            group.remove(request.member);
        }
    }

}
