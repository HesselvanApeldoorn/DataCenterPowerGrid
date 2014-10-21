import java.io.Serializable;

class Message implements Serializable {
    public boolean is_ordered;
    public boolean is_multicast;
    public     int sequence_nr;
}
