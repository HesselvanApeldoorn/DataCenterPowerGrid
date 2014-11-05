import java.net.SocketAddress;
import java.util.TimerTask;
import java.util.Timer;
import java.util.Random;

class Member implements Dispatcher.Endpoint {
    private Group      group;
    private Middleware middleware;
    private Election   election = null;
    private Leader     leader   = null;

    private long      leaderPid = Middleware.NO_PID;
    private long            pid = Middleware.NO_PID;
    private long  lastHeartbeat = -1;
    private SocketAddress votedFor = null;
    private int     currentTerm = 0;

    public Member(Group theGroup, Middleware theMiddleware, boolean candidate) {
        this.group      = theGroup;
        this.middleware = theMiddleware;
        if (candidate) {
            this.election = new Election(Leader.HEARTBEAT_PERIOD);
            this.middleware.getTimer().schedule(this.election, this.election.timeout, this.election.timeout);
        }
    }

    private class Election extends TimerTask {
        /* Only possible reason to run an election is if you can be a candidate as well */
        public final long timeout;

        public boolean active = false;
        private int estimatedGroupSize;
        private int totalVotes;
        private int positiveVotes;

        public Election(long period) {
            this.timeout = 2*period + (new Random().nextLong() % period);
        }

        @Override
        public void run() {
            System.err.println("Election.run");
            long now = System.currentTimeMillis();
            if (active) {
                countVotes();
            } else if (lastHeartbeat < (now - timeout) && votedFor == null) {
                startElection();
            }
        }

        private void startElection() {
            System.err.println("startElection");
            currentTerm += 1;
            totalVotes = 0;
            positiveVotes = 0;
            active = true;
            if (leaderPid != Middleware.NO_PID && pid != Middleware.NO_PID) {
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
                if (pid == Middleware.NO_PID)
                    pid = group.nextPid();
                leader = new Leader(group, middleware, pid, currentTerm);
                middleware.getTimer().schedule(leader, Leader.HEARTBEAT_PERIOD, Leader.HEARTBEAT_PERIOD);
                middleware.sendGroup(new Leader.Heartbeat(System.currentTimeMillis(), pid, currentTerm), false);
            }
             active = false;
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
        public final long member;
        public Leave(long member) {
            this.member  = member;
        }
    }

    public static class Join extends Message {
        /* Announce the join request */
        public final long member;
        public final SocketAddress address;
        public Join(long member, SocketAddress address) {
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

    public void onHeartbeat(long sender, SocketAddress address, long timestamp, Leader.Heartbeat message) {
        System.err.printf("onHeartbeat(%s, %d, %d);\n", address.toString(), timestamp, message.term);
        // in theory, compute the difference between our received timestamp and
        // the leaders timestamp. In practice, just reply saying you're alive
        lastHeartbeat = timestamp;
        // cancel any running elections
        election.active = false;
        votedFor        = null;
        if (message.term >= currentTerm) {
            if (sender == Middleware.NO_PID) {
                // This is a unknown, but valid leader for me, so I
                // can trust it's messages. Therefore, I add it to the
                // group.
                group.add(message.pid, address);
                leaderPid = message.pid;
            }
            // I didn't send this to myself, and there is some other leader.
            if (message.pid != pid && leader != null) {
                System.err.println("Standing down");
                leader.cancel();
                leader = null;
            }
        }
        // respond with a life sign
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
        this.pid = welcome.pid;
    }

    public long getPid() {
        return this.pid;
    }

    public void onVoteRequest(SocketAddress sender, int term) {
        System.err.printf("onVoteRequest(%s, %d);\n", sender.toString(), term);
        if (term > currentTerm || (term == currentTerm && votedFor == null)) {
            votedFor    = sender;
            currentTerm = term;
            middleware.send(sender, new VoteReply(true, term));
        } else {
            middleware.send(sender, new VoteReply(false, term));
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
            onVoteRequest(message.packet.getSocketAddress(), req.term);
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
