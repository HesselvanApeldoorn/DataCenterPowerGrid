import java.net.DatagramPacket;
import java.util.concurrent.BlockingQueue;



public class Dispatcher extends Thread {
	private Middleware middleware;
	boolean stopped;
	
	public Dispatcher(Middleware middleware) {
		this.middleware = middleware;
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
			System.out.println(" known pids: " + middleware.getGroup().getPids());
			// for (long pidz : middleware.getGroup().getPids()) 
				// System.out.println(pidz + ", " + middleware.getGroup().getAddress(pidz));
    	    if (receivedMessage.payload instanceof ElectionMessage) {
    	        middleware.getMembership().participateElection(receivedMessage);
    	    } else if (receivedMessage.payload instanceof BullyElectionMessage) {
    	    	middleware.getMembership().cancelElection();
    	    } else if (receivedMessage.payload instanceof HeartbeatMessage) {
    	    	middleware.getMembership().updateHeartbeat(receivedMessage);
    	    } else if (receivedMessage.payload instanceof AckHeartbeatMessage) {
    	    	this.middleware.getLeader().receiveAcknowledge(receivedMessage);
    	    } else if(receivedMessage.payload instanceof JoinMessage) {
    	    	if (middleware.getMembership().isLeader())
    	    		this.middleware.getLeader().handoutPid(receivedMessage);
    	    } else if (receivedMessage.payload instanceof AckJoinMessage) {
    	    	middleware.getMembership().applyJoin(receivedMessage);
    	    } else if (receivedMessage.payload instanceof LeaveMessage) {
    	    	middleware.getGroup().removeProcess(receivedMessage);
    	    } else { // TODO: not implemented yet, how to handle other messages?
    	    	System.out.println("received message, thats not handled yet: " + receivedMessage.packet.getClass());
    	    }
		}
    }

}
