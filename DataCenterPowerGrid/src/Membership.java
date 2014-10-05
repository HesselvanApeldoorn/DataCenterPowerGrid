import java.util.TimerTask;
import java.util.Timer;

class Membership {
    public final static long HEARTBEAT_PERIOD = 5000;

    private Group   group;
    private Middleware middleware;
    private boolean isLeader = false;
    private boolean inElection = false;
    private long    mostRecentHeartbeat = -1;

    public Membership(Group theGroup, Middleware theMiddleware,
                      boolean iCanLead) {
        this.group      = theGroup;
        this.middleware = theMiddleware;
        this.canLead    = iCanLead;
        this.middleware.getTimer().schedle(new Heartbeat(),
                                           HEARTBEAT_PERIOD,
                                           HEARTBEAT_PERIOD);
    }

    public void receive(Message message) {

    }

    private class Heartbeat extends TimerTask {
        public void run() {
            long now = System.currentTimeMillis();
            if (isLeader) {
                // pass
            } else if (inElection) {
                // ehm..
            } else {
                // if we have missed too many heartbeats,
                // then we should call an election
                if (now - mostRecentHeartbeat >= (2 * HEARTBEAT_PERIOD)) {
                    inElection = true;
                    // send out an election message. how?
                }
            }
        }
    }

}
