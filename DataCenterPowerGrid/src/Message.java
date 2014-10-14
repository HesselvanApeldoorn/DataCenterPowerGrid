import java.io.Serializable;

class Message implements Serializable {
    public long senderPid;
    public long timeStamp;

    
    public Message(long senderPid, long timeStamp) {
    	this.senderPid = senderPid;
    	this.timeStamp = timeStamp;
    }
}
