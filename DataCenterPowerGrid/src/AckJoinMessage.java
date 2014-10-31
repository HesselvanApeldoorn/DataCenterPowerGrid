import java.net.SocketAddress;


public class AckJoinMessage extends Message {
	long pid;
	SocketAddress address;
	
	public AckJoinMessage(long pid, SocketAddress address) {
		this.pid = pid;
		this.address = address;
	}
}
