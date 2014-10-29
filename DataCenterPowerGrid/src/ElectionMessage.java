
public class ElectionMessage extends Message {
    public long sender_pid;
    public ElectionMessage(long sender_pid) {
        this.sender_pid = sender_pid;
    }
}
