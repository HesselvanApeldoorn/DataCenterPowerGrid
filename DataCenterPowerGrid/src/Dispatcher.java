import java.util.concurrent.BlockingQueue;

class Dispatcher extends Thread {
    private BlockingQueue<Message> queue;
    private Membership membership;
    private boolean isRunning;

    public Dispatcher(BlockingQueue<Message> messageQueue) {
        this.queue = messageQueue;
    }

    public void run() {
        isRunning = true;
        try {
            while(isRunning) {
                Message message = queue.take();
                dispatchMessage(message);
            }
        } catch (InterruptedException ex) {
            isRunning = false; // o.O
        }
    }

    private void dispatchMessage(Message message) {
        switch(message.type) {
        case HEARTBEAT:
        case ACKNOWLEDGE:
            membership.receive(message);
            break;
        }
    }
}
