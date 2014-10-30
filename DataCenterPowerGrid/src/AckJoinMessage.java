import java.net.SocketAddress;


public class AckJoinMessage extends Message {
	long pid;
	
	public AckJoinMessage(long pid) {
		this.pid = pid;
	}
}
