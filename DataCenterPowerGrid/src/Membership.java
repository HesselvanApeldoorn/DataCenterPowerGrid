import java.util.Iterator;
import java.util.TimerTask;
import java.util.Timer;
import java.util.concurrent.BlockingQueue;


class Membership {
    public final static long HEARTBEAT_PERIOD = 5000;

    private Group   group;
    private Middleware middleware;
    private boolean isLeader = false;
    private boolean canLead = false;
    private boolean inElection = false;
    private long    mostRecentHeartbeat = System.currentTimeMillis(); // TODO: set back to -1, test purposes
    private long    pid;

    public Membership(Group theGroup, Middleware theMiddleware,
                      boolean iCanLead, long pid) {
        this.group      = theGroup;
        this.middleware = theMiddleware;
        this.canLead    = iCanLead;
        this.middleware.getTimer().schedule(new Heartbeat(),
                                            HEARTBEAT_PERIOD,
                                            HEARTBEAT_PERIOD);
        this.pid = pid;
    }


    private class Heartbeat extends TimerTask {
        public void run() {
            long now = System.currentTimeMillis();
            BlockingQueue<Middleware.ReceivedMessage> queue = middleware.getDeliveryQueue();
        	Iterator<Middleware.ReceivedMessage> it = queue.iterator();
        	while(it.hasNext()) {
        	    Middleware.ReceivedMessage receivedMessage = it.next();
        	    if(receivedMessage.payload instanceof ElectionMessage) {
        	        ElectionMessage message = (ElectionMessage) receivedMessage.payload;
        	        if (message.sender_pid < pid) {  // my pid is higher than broadcast pid
        	        	middleware.send(message.sender_pid, new BullyElectionMessage(), false);
        	        	middleware.getTimer().schedule(new Election(), HEARTBEAT_PERIOD, HEARTBEAT_PERIOD);
        	        }
        	    }
        	}
            if (canLead && mostRecentHeartbeat < now - 2 * HEARTBEAT_PERIOD) { // Leader is unavailable, start election
                middleware.getTimer().schedule(new Election(),
                        HEARTBEAT_PERIOD,
                        HEARTBEAT_PERIOD);
                this.cancel(); // I'm busy with election, don't listen to others
            }  
        }
    }

    private class Election extends TimerTask {
    	long leaderTime = System.currentTimeMillis();;
        public void run() {
        	System.out.println("started election on pid: " + pid);
        	middleware.sendGroup(new ElectionMessage(pid), false);
        	isLeader = true;
            BlockingQueue<Middleware.ReceivedMessage> queue = middleware.getDeliveryQueue();
        	Iterator<Middleware.ReceivedMessage> it = queue.iterator();
        	while(it.hasNext()) {
        	    Middleware.ReceivedMessage receivedMessage = it.next();
        	    System.out.println("received message");
        	    if(receivedMessage.payload instanceof BullyElectionMessage) { // there is a process with higher pid 
//        	    	queue.remove(receivedMessage);  // remove election message from queue, shouldn't appear in future elections
        	        isLeader = false;
        	    }
        	}
        	// received no higher pids, this process is therefore leader
        	if (isLeader && (leaderTime + 2*HEARTBEAT_PERIOD < System.currentTimeMillis())) { 
        		System.out.println("I'm leader, my pid is: " + pid);
	        	this.cancel();
	            middleware.getTimer().schedule(new Heartbeat(),
                        HEARTBEAT_PERIOD,
                        HEARTBEAT_PERIOD);  // done with election start heartbeats again
        	}
        }
    }

}
