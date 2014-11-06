import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;

class Dispatcher implements Runnable {
    public interface Endpoint {
        public void deliver(Middleware.ReceivedMessage message) throws Stop;
    }

    public enum Type {
        // group stuff
        HEARTBEAT,
        WELCOME,
        ALIVE,
        JOIN,
        LEAVE,
        VOTE_REQUEST,
        VOTE_REPLY,
        // energy stuff
        SALE,
        ORDER,
    }


    private static final Map<Class<? extends Message>, Type> typeMap = new HashMap<Class<? extends Message>, Type>();

    static {
        typeMap.put(Leader.Heartbeat.class, Type.HEARTBEAT);
        typeMap.put(Leader.Welcome.class, Type.WELCOME);
        typeMap.put(Member.Alive.class, Type.ALIVE);
        typeMap.put(Member.Join.class, Type.JOIN);
        typeMap.put(Member.Leave.class, Type.LEAVE);
        typeMap.put(Member.VoteRequest.class, Type.VOTE_REQUEST);
        typeMap.put(Member.VoteReply.class, Type.VOTE_REPLY);
        typeMap.put(EnergyAuction.Sale.class, Type.SALE);
        typeMap.put(EnergyAuction.Order.class, Type.ORDER);
    }

    public static Type getMessageType(Middleware.ReceivedMessage message) {
        return typeMap.get(message.payload.getClass());
    }

    public class Stop extends Exception {}

    private final BlockingQueue<Middleware.ReceivedMessage> queue;
    private final Member member;
    private final Endpoint application;

    public Dispatcher(BlockingQueue<Middleware.ReceivedMessage> queue, Member member, Endpoint application) {
        this.queue       = queue;
        this.member      = member;
        this.application = application;
    }

    public void run() {
        try {
            while (true) {
                Middleware.ReceivedMessage message = queue.take();
                switch(getMessageType(message)) {
                case JOIN:
                case LEAVE:
                case WELCOME:
                case ALIVE:
                case HEARTBEAT:
                case VOTE_REQUEST:
                case VOTE_REPLY:
                    member.deliver(message);
                    break;
                default:
                    application.deliver(message);
                    break;
                }
            }
        } catch (InterruptedException e) {
            return;
        } catch (Stop e) {
            return;
        }
    }
}
