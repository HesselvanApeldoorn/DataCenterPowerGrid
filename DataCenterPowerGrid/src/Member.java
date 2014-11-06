import java.net.SocketAddress;
import java.util.TimerTask;
import java.util.Timer;
import java.util.Random;

class Member implements Dispatcher.Endpoint {
    private Group      group;
    private Middleware middleware;
    private EnergyAuction auction;
    private Election   election = null;
    private Leader     leader   = null;

    private int      leaderPid = Middleware.NO_PID;
    private int    currentTerm = 0;
    private long lastHeartbeat = -1;


    public Member(Group theGroup, Middleware theMiddleware, EnergyAuction auction) {
        this.group      = theGroup;
        this.middleware = theMiddleware;
        this.auction    = auction;
        this.election = new Election(Leader.HEARTBEAT_PERIOD);
        this.middleware.getTimer().schedule(this.election, this.election.timeout, this.election.timeout);

    }

    private class Election extends TimerTask {
        /* Only possible reason to run an election is if you can be a candidate as well */
        public final long period;
        public final long timeout;

        public boolean active = false;
        private int estimatedGroupSize;
        private int totalVotes;
        private int positiveVotes;

        public Election(long period) {
            this.period = period;
            this.timeout = 2*period + (new Random().nextLong() % period);
        }

        @Override
        public void run() {
            System.err.println("Election.run");
            long now = System.currentTimeMillis();
            if (active) {
                countVotes();
            } else if (canVote()) {
                // todo this check will never start  a second election. what to do?.
                startElection();
            }
        }

        public boolean canVote() {
            return lastHeartbeat < (System.currentTimeMillis() - 2*period);
        }

        private void startElection() {
            System.err.println("startElection");
            currentTerm += 1;
            totalVotes = 0;
            positiveVotes = 0;
            active = true;
            if (leaderPid != Middleware.NO_PID && middleware.getPid() != Middleware.NO_PID) {
                group.remove(leaderPid);
                middleware.sendGroup(new Leave(leaderPid), true);
            }
            estimatedGroupSize = group.getSize();
            middleware.sendGroup(new VoteRequest(currentTerm), false);
        }

        private void countVotes() {
            System.err.println("countVotes");
            // NB, totalVotes will be less than the group size due to message loss
            // thus in theory there can be more than one leader elected. however,
            // in that case each will 'kick out' the other leader swiftly, and
            // another leader will start
            int goalPost = (estimatedGroupSize > totalVotes ? estimatedGroupSize : totalVotes);
            if (positiveVotes * 2 > goalPost) {
                // take max of total votes and group size
                System.err.println("I declare myself leader");
                // we have a majority, start leading
                // assign myself a pid if i don't have one yet
                if (middleware.getPid() == Middleware.NO_PID)
                    middleware.setPid(group.nextPid());
                leader = new Leader(group, middleware, currentTerm);
                leader.sendHeartbeat();
                middleware.getTimer().schedule(leader, Leader.HEARTBEAT_PERIOD, Leader.HEARTBEAT_PERIOD);
                // start auction broker
                auction.startBroker(Leader.HEARTBEAT_PERIOD);
            }
            active   = false;
        }

        public void onVoteReply(boolean reply, int term) {
            System.err.println("Vote reply: " + (reply ? "true" : "false"));
            if (term != currentTerm) // an out-of-order message
                return;
            totalVotes++;
            if (reply)
                positiveVotes++;
        }
    }



    public static class Alive extends Message {
        // nothing to do here, move along
    }

   public static class Leave extends Message {
        public final int member;
        public Leave(int member) {
            this.member  = member;
        }
    }

    public static class Join extends Message {
        /* Announce the join request */
        public final int member;
        public final SocketAddress address;
        public Join(int member, SocketAddress address) {
            this.member  = member;
            this.address = address;
        }
    }

    public static class VoteRequest extends Message {
        public final int term;
        public VoteRequest(int term) {
            this.term    = term;
        }
    }

    public static class VoteReply extends Message {
        public final boolean reply;
        public final int term;
        public VoteReply(boolean reply, int term) {
            this.reply = reply;
            this.term   = term;
        }
    }

    public void onHeartbeat(int sender, SocketAddress address, long timestamp, Leader.Heartbeat message) {
        System.err.printf("onHeartbeat(%s, %d, %d);\n", address.toString(), timestamp, message.term);
        lastHeartbeat = timestamp;
        // cancel any running elections
        election.active = false;
        if (message.term >= currentTerm) {
            if (sender == Middleware.NO_PID) {
                // This is a unknown, but valid leader for me, so I
                // can trust it's messages. Therefore, I add it to the
                // group.
                group.add(message.pid, address);
                leaderPid = message.pid;
            }
            // I didn't send this to myself, and there is some other leader.
            if (message.pid != middleware.getPid() && leader != null) {
                System.err.println("Standing down my leadership");
                leader.cancel();
                auction.stopBroker();
                leader = null;
            }
            currentTerm = message.term;
        }
        // compare message state
        middleware.compareMessageState(message.state);
        // in theory, compute the difference between our received timestamp and
        // the leaders timestamp. In practice, just reply saying you're alive
        middleware.send(address, new Alive());
    }

    public void onJoin(Join request) {
        System.err.printf("Join %d %s\n", request.member, request.address);
        group.add(request.member, request.address);
    }

    public void onLeave(Leave request) {
        System.err.printf("Leave %d\n", request.member);
        group.remove(request.member);
    }

    public void onWelcome(Leader.Welcome welcome) {
        System.err.printf("Welcome %d (%d)\n", welcome.pid, welcome.sequence_nr);
        this.middleware.setPid(welcome.pid);
    }

    public void onVoteRequest(long timestamp, SocketAddress sender, int term) {
        System.err.printf("onVoteRequest(%s, %d);\n", sender.toString(), term);
        if (term < currentTerm) {
            middleware.send(sender, new VoteReply(false, term));
        } else if ((term == currentTerm && election.canVote()) || term > currentTerm) {
            currentTerm   = term;
            lastHeartbeat = timestamp;
            middleware.send(sender, new VoteReply(true, term));
        }
    }

    public void deliver(Middleware.ReceivedMessage message) {
        switch (Dispatcher.getMessageType(message)) {
        case JOIN: {
            onJoin((Join)message.payload);
            break;
        }
        case LEAVE: {
            onLeave((Leave)message.payload);
            break;
        }
        case WELCOME: {
            onWelcome((Leader.Welcome)message.payload);
            break;
        }
        case ALIVE: {
            if (leader != null) {
                leader.onAlive(message.sender, message.packet.getSocketAddress(), message.timestamp);
            }
            break;
        }
        case HEARTBEAT: {
            onHeartbeat(message.sender, message.packet.getSocketAddress(),
                        message.timestamp, (Leader.Heartbeat)message.payload);
            break;
        }
        case VOTE_REQUEST: {
            VoteRequest req = (VoteRequest)message.payload;
            onVoteRequest(message.timestamp, message.packet.getSocketAddress(), req.term);
            break;
        }
        case VOTE_REPLY: {
            VoteReply rep = (VoteReply)message.payload;
            if (election.active) {
                election.onVoteReply(rep.reply, rep.term);
            }
            break;
        }
        default:
            System.err.println(message);
        }
    }
}
