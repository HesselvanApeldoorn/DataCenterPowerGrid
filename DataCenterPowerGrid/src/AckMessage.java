
public class AckMessage extends Message {
	public long timeStamp;
	public long pid;
	public AckMessage(long timeStamp, long pid) {
		super(0);
		this.timeStamp = timeStamp;
		this.pid = pid;
	}

}
