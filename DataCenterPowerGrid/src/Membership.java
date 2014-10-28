import java.util.TimerTask;
import java.util.Timer;

class Membership {
    public final static long HEARTBEAT_PERIOD = 5000;

    private Group   group;
    private Middleware middleware;
    private boolean isLeader = false;
    private boolean canLead = false;
    private boolean inElection = false;
    private long    pid;

    public Membership(Group theGroup, Middleware theMiddleware,
                      boolean iCanLead) {
        this.group      = theGroup;
        this.middleware = theMiddleware;
        this.canLead    = iCanLead;
    }



}
