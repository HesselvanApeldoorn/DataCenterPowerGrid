import java.io.Serializable;

class Message implements Serializable {
    public int num;
    
    public Message(int val) {
    	this.num = val;
    }
    
    public String toString() {
    	
    	return String.format("Message %d", num);
    }
}
