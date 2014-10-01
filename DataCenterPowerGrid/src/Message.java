import java.net.DatagramPacket;

class Message {
    final byte[]         data;
    final long           timestamp;
    final DatagramPacket packet;

    public Message(long aTimestamp, byte[] someData, DatagramPacket aPacket) {
        this.timestamp = aTimestamp;
        this.data      = someData;
        this.packet   = aPacket;
    }
}
