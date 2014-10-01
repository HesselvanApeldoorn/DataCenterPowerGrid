import java.util.Set;
import java.util.HashSet;
import java.util.TimerTask;
import java.util.Timer;

class Group implements Middleware {
    public final static long HEARTBEAT_PERIOD = 5000;

    private boolean isLeader = false;
    private boolean inElection = false;
    private long    mostRecentHeartbeat = -1;
    private Timer   timer;
    private Set     members;

    private class Heartbeat extends TimerTask {
        public void run() {
            long now = System.currentTimeMillis();
            if (isLeader) {
                // what to do - check if all group members have
                // acknowledged
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

    public Group(Timer theTimer) {
        this.timer = theTimer;
        this.timer.schedule(new Heartbeat(), HEARTBEAT_PERIOD, 
                            HEARTBEAT_PERIOD);
        this.members = new HashSet();
    }


    public Message onSend(Message aMessage) {
        // nothing to do here. move along
        return null;
    }

    public Message onReceive(Message aMessage) {
        // hmm.. not sure how to do this yet.  we'll handle the
        // following types of messages (note - we'll have to do
        // message decoding first. (or do we?. there is a good
        // argument to be made in that each 'layer' gets to set it's
        // own header.)
        return null;
    }
}
