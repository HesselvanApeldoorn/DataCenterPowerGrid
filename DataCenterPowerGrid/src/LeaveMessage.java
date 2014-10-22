
public class LeaveMessage extends Message {
    public long groupVersion;
    public long pid;
    public LeaveMessage(long groupVersion, long pid) {
        super(0);
        this.groupVersion = groupVersion;
        this.pid = pid;
    }
}
