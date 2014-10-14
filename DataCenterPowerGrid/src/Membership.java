import java.util.TimerTask;
import java.util.Timer;

class Membership {
    public final static long HEARTBEAT_PERIOD = 5000;

    private Group   group;
    private Middleware middleware;
    private boolean isLeader = false;
    private boolean canLead = false;
    private boolean inElection = false;
    private long    mostRecentHeartbeat = -1;
    private long    pid;

    public Membership(Group theGroup, Middleware theMiddleware,
                      boolean iCanLead) {
        this.group      = theGroup;
        this.middleware = theMiddleware;
        this.canLead    = iCanLead;
        this.middleware.getTimer().schedule(new Heartbeat(),
                                            HEARTBEAT_PERIOD,
                                            HEARTBEAT_PERIOD);
    }


    private class Heartbeat extends TimerTask {
        public void run() {
            long now = System.currentTimeMillis();
            /* TODO: Message is not yet fully implemented, code underneath will not run perfectly yet*/
            if (!isLeader) middleware.sendGroup(new Message(pid, now));
        }
    }

}
