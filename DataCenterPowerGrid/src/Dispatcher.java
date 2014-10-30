import java.net.DatagramPacket;
import java.util.concurrent.BlockingQueue;



public class Dispatcher extends Thread {
	private Middleware middleware;
	private Leader leader;
	boolean stopped;
	
	public Dispatcher(Middleware middleware, Leader leader) {
		this.middleware = middleware;
		this.leader = leader;
		stopped = false;
	}
	
    public void run() {
        stopped = false;
        while (!stopped) {
		    BlockingQueue<Middleware.ReceivedMessage> queue = middleware.getDeliveryQueue();
		    Middleware.ReceivedMessage receivedMessage = null;
			try {
				receivedMessage = queue.take();
			} catch (InterruptedException e) {
				System.out.println("Couldn't take message from queue");
			}
    	    if (receivedMessage.payload instanceof ElectionMessage) {
    	        middleware.getMembership().participateElection(receivedMessage);
    	    } else if (receivedMessage.payload instanceof BullyElectionMessage) {
    	    	middleware.getMembership().cancelElection();
    	    } else if (receivedMessage.payload instanceof HeartbeatMessage) {
    	    	middleware.getMembership().updateHeartbeat(receivedMessage);
    	    } else if(receivedMessage.payload instanceof JoinMessage) {
    	    	if (middleware.getMembership().isLeader())
    	    		this.leader.handoutPid(receivedMessage);
    	    } else if (receivedMessage.payload instanceof AckJoinMessage) {
    	    	middleware.getGroup().applyJoin(receivedMessage);
    	    	middleware.getMembership().updatePid(receivedMessage);
    	    } else { // TODO: not implemented yet, how to handle other messages?
    	    	System.out.println("received message, thats not handled yet: " + receivedMessage.packet.getClass());
    	    }
		}
    }

}
