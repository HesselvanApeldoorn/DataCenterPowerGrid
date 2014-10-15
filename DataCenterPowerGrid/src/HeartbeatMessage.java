
public class HeartbeatMessage extends Message {
	public long timeStamp;
	public HeartbeatMessage(long myTime) {
		super(0);
		timeStamp = myTime;
	}
}
