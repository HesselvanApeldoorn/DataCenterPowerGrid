import java.net.BlockingQueue;

class Dispatcher extends Thread {
    private BlockingQueue<Message> queue;
    private Membership membership;
    private boolean isRunning;

    public void run() {
        isRunning = true;
        try {
            while(isRunning) {
                Message message = queue.take();
                dispatchMessage(message);
            }
        } catch (InterruptedException ex) {
            isRunning = false;
        }
    }

    private void dispatchMessage(Message message) {
        switch(message.getType()) {
        case Message.Type.HEARTBEAT:
        case Message.Type.ACKNOWLEDGE:
            membership.receive(message);
            break;
        }
    }
}
