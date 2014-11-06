import java.util.Timer;
import java.util.TimerTask;
import java.util.Random;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

class EnergyAuction implements Dispatcher.Endpoint {
    public final static long AUCTION_PERIOD = 60000;// one minute
    private final Middleware middleware;
    private final double startingPrice;
    private Job job;
    private Broker broker;
    private double lastPrice;
    // persistent orders haha
    private Map<Integer, List<Order>> orders;

    public static class Sale extends Message {
        public final long startTime;
        public final long durationMs;
        public final double priceKwh;

        public Sale(long startTime, long duration, double priceKwh) {
            this.startTime  = startTime;
            this.durationMs = duration;
            this.priceKwh   = priceKwh;
        }
    }

    public static class Order extends Message {
        public final Sale sale;
        public final double orderKwh;

        public Order(Sale sale, double orderKwh) {
            this.sale     = sale;
            this.orderKwh = orderKwh;
        }
    }

    public static class Job {
        private double completed;
        private double spent;
        private final double totalValue;
        private final double workLoad;
        private final double workPerKwh;
        public Job(double totalValue, double workLoad, double workPerKwh) {
            this.totalValue = totalValue;
            this.workLoad = workLoad;
            this.workPerKwh = workPerKwh;
            this.completed = 0.0;
        }

        public Order computeOrder(Sale sale) {
            // todo compute an order based on price
            return new Order(sale, (Math.random()+0.5)*5.0);
        }

        public boolean isComplete() {
            return this.completed >= 1.0;
        }

        public static Job newJob(double startPriceKwh) {
            double workLoad = 100*(Math.random() + 0.2);
            double workPerKwh = 30 * (Math.random() + 0.5);
            double maximumPrice = startPriceKwh * (Math.random() + 0.5);
            double totalValue = maximumPrice * (workLoad / workPerKwh);
            return new Job(totalValue, workLoad, workPerKwh);
        }
    }

    public class Broker extends TimerTask {
        private final Random random;
        private final long period;
        public Broker(long period) {
            this.random = new Random();
            this.period = period;
        }

        public void run() {
            double newPrice = Math.max(startingPrice * 0.2,
                                       lastPrice + (random.nextGaussian() * lastPrice * 0.25));
            long startTime = System.currentTimeMillis() + period;
            middleware.sendGroup(new Sale(startTime, period, newPrice), true);
            lastPrice = newPrice;
        }
    }

    public EnergyAuction(Middleware middleware, double startingPrice) {
        this.middleware = middleware;
        this.startingPrice = startingPrice;
        this.lastPrice = startingPrice;
        this.orders    = new HashMap<Integer, List<Order>>(100);
        this.job       = Job.newJob(startingPrice);
    }

    public void startBroker(long delay) {
        broker = new Broker(AUCTION_PERIOD);
        middleware.getTimer().schedule(broker, delay, AUCTION_PERIOD);
    }

    public void stopBroker() {
        broker.cancel();
    }

    public void onOrder(int sender, Order order) {
        if (!orders.containsKey(sender))
            orders.put(sender, new ArrayList<Order>(10));
        // such persistence. much wow
        orders.get(sender).add(order);
    }

    public void onSale(Sale sale) {
        if (job.isComplete()) {
            job = Job.newJob(lastPrice);
        }
        Order order = job.computeOrder(sale);
        middleware.sendGroup(order, true);
        lastPrice = sale.priceKwh;
    }

    public void deliver(Middleware.ReceivedMessage message) {
        switch (Dispatcher.getMessageType(message)) {
        case SALE:
            onSale((Sale)message.payload);
            break;
        case ORDER:
            onOrder(message.sender, (Order)message.payload);
            break;
        default:
            System.err.println(message.payload);
        }
    }
}
