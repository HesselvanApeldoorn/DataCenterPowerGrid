import java.net.DatagramPacket;
import java.util.concurrent.BlockingQueue;



public class Dispatcher extends Thread {
	Middleware middleware;
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
    	    if (receivedMessage.payload instanceof ElectionMessage) {
    	        ElectionMessage message = (ElectionMessage) receivedMessage.payload;
    	        middleware.getMembership().participateElection(message);
    	    } else if (receivedMessage.payload instanceof BullyElectionMessage) {
    	    	middleware.getMembership().cancelElection();
    	    } else { // TODO: not implemented yet, how to handle other messages?
    	    	System.out.println("received message, thats not handled yet: " + receivedMessage);
    	    }
		}
    }

}
