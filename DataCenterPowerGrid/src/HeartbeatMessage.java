
public class HeartbeatMessage extends Message {
    public long timeStamp;
    public HeartbeatMessage(long myTime) {
        timeStamp = myTime;
    }
}
