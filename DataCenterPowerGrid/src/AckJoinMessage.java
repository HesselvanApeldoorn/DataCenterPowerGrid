import java.net.SocketAddress;


public class AckJoinMessage extends Message {
	long pid;
	SocketAddress address;
	Group group;
	
	public AckJoinMessage(long pid, SocketAddress address, Group group) {
		this.pid = pid;
		this.address = address;
		this.group = group;
	}
}
