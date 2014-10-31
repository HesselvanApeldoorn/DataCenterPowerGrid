import java.util.TimerTask;


class Membership {
    public final static long HEARTBEAT_PERIOD = 5000;

    private Group   group;
    private Middleware middleware;
    private boolean isLeader = false;
	private boolean canLead = false;
    private boolean inElection = false;
    private long    mostRecentHeartbeat = System.currentTimeMillis(); // TODO: set back to -1, test purposes
    private long    pid;
	Election election;

    public Membership(Group theGroup, Middleware theMiddleware,
                      boolean iCanLead, long pid) {
        this.group      = theGroup;
        this.middleware = theMiddleware;
        this.canLead    = iCanLead;
        this.middleware.getTimer().schedule(new Heartbeat(),
                                            HEARTBEAT_PERIOD,
                                            HEARTBEAT_PERIOD);
        this.pid = pid;
        election = new Election();
    }


    private class Heartbeat extends TimerTask {
        public void run() {
            long now = System.currentTimeMillis();
            if (canLead && !inElection && ( mostRecentHeartbeat < now - 2 * HEARTBEAT_PERIOD)) { // Leader is unavailable, start election
            	System.out.println("start election");  // TODO: something going wrong here, elections are started twice somehow
            	middleware.sendGroup(new ElectionMessage(pid), false);
            	middleware.getTimer().schedule(election,
                        HEARTBEAT_PERIOD*2,
                        HEARTBEAT_PERIOD*2);
            }  
        }
    }

    private class Election extends TimerTask {
        public void run() {
        	inElection = true;
        	// received no higher pids, this process is therefore leader
        	if (isLeader) { 
        		System.out.println("I'm leader, my pid is: " + pid);
	        	this.cancel();
	            middleware.getTimer().schedule(new Leader(group, middleware),
                        HEARTBEAT_PERIOD,
                        HEARTBEAT_PERIOD);  // I'm leader, start leader process
        		inElection = false;
        	}
        	isLeader = true;
        }
    }

	public void participateElection(Middleware.ReceivedMessage receivedMessage) {
        ElectionMessage message = (ElectionMessage) receivedMessage.payload;
		if (message.sender_pid < pid) {  // my pid is higher than broadcast pid
        	System.out.println("Sent bully message");
        	middleware.send(message.sender_pid, new BullyElectionMessage(), false);
        	middleware.getTimer().schedule(election, HEARTBEAT_PERIOD, HEARTBEAT_PERIOD);
        }
	}

	public void cancelElection() {
		this.inElection = false;
		this.isLeader = false;
		this.election.cancel();
		this.mostRecentHeartbeat = System.currentTimeMillis();
	}

	public void updateHeartbeat(Middleware.ReceivedMessage receivedMessage) {
        HeartbeatMessage message = (HeartbeatMessage) receivedMessage.payload;
		this.mostRecentHeartbeat = message.timeStamp;
		middleware.send(0, new AckHeartbeatMessage(), false);  // TODO: pid shouldn't be 0, should be retrieved from leader process instead, but it is the default for the first leader
	} // TODO: leader pid should be retrievable from ackjoinmessage or heartbeatmessage
	
    public boolean isLeader() {
		return isLeader;
	}
    
    public void setLeader(boolean isLeader) {
    	this.isLeader = isLeader;
    }
    
    public void setPid(long pid) {
    	this.pid = pid;
    }

	public void applyJoin(Middleware.ReceivedMessage receivedMessage) {
        AckJoinMessage message = (AckJoinMessage) receivedMessage.payload;
        this.pid = message.pid;
        this.group = message.group;
        middleware.setGroup(message.group);
        System.out.println(" applying join, group pids: " + group.getPids());
        System.out.println("successfully updated pid and group");
	}
}
