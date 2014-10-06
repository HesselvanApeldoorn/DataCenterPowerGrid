import java.io.Serializable;

class Message implements Serializable {
    public static enum Type {
        HEARTBEAT,
        ACKNOWLEDGE
    }
    public Type type;
    public long senderPid;
    public long timeStamp;
}
