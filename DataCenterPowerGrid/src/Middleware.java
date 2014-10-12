
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.MulticastSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;
import java.util.Timer;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

class Middleware extends Thread {
    private DatagramSocket  privateSocket;
    private Receiver        privateReceiver;
    private MulticastSocket groupSocket;
    private Receiver        groupReceiver;

    private BlockingQueue<DatagramPacket> inputQueue;
    private BlockingQueue<DatagramPacket> outputQueue;
    private BlockingQueue<Message>        deliveryQueue;
    private Group group;
    private Timer timer;
    private boolean stopped;

    public void Middleware(DatagramSocket thePrivateSocket,
                           MulticastSocket theGroupSocket) {
        this.privateSocket   = thePrivateSocket;
        this.groupSocket     = theGroupSocket;
        this.inputQueue      = new LinkedBlockingQueue<DatagramPacket>();
        this.outputQueue     = new ArrayBlockingQueue<DatagramPacket>(10);
        this.privateReceiver = new Receiver(inputQueue, privateSocket);
        this.groupReceiver   = new Receiver(outputQueue, groupSocket);

    }

    public void send(long pid, Message msg) {
        SocketAddress address = this.group.getAddress(pid);
        DatagramPacket packet = encodeMessage(address, msg);
        try {
            this.outputQueue.put(packet);
        } catch (InterruptedException ex) {
            System.out.println("Dropped message because of interrupt");
        }
    }

    public void sendGroup(Message msg) {
        DatagramPacket packet = encodeMessage(null, msg);
        try {
            this.outputQueue.put(packet);
        } catch (InterruptedException ex) {
            System.out.println("Dropped group message because of interrupt");
        }
    }

    public void run() {
        stopped = false;
        try {
            while (!stopped) {
                DatagramPacket packet = this.inputQueue.take();
                Message       message = decodeMessage(packet);
                // do reordering if necessary, no-op for now
                this.deliveryQueue.put(message);
            }
        } catch (InterruptedException ex) {
            // guess somebody wanted us to stop
            stopped = true;
        }
        this.timer.cancel();
    }

    private Message decodeMessage(DatagramPacket packet) {
        long now = System.currentTimeMillis();
        try {
            ObjectInputStream stream = new ObjectInputStream(
                    new ByteArrayInputStream(packet.getData(), packet.getOffset(), packet.getLength())
            );
            Message message = (Message) stream.readObject();
            message.timeStamp = now;
            return message;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (ClassCastException e) {
            e.printStackTrace();
        }
        return null;
    }

    private DatagramPacket encodeMessage(SocketAddress addr,
                                         Message message) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(8192);
            ObjectOutputStream stream = new ObjectOutputStream(buffer);
            stream.writeObject(message);
            stream.close();
            return new DatagramPacket(buffer.toByteArray(), buffer.size(), addr);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /* So that we may all use the same timer */
    public Timer getTimer() {
        return timer;
    }
}
