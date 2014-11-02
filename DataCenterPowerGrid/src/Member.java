import java.net.SocketAddress;
import java.util.TimerTask;
import java.util.Timer;
import java.util.Random;

class Member implements Dispatcher.Endpoint {
    private Group      group;
    private Middleware middleware;
    private Election   election = null;
    private Leader     leader   = null;

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
        private int totalVotes;
        private int positiveVotes;

        public Election(long period) {
            this.timeout   = 3*period + (new Random().nextLong() % period);
        }

        @Override
        public void run() {
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
            middleware.sendGroup(new VoteRequest(currentTerm, group.getVersion()), false);
        }

        private void countVotes() {
            System.err.println("countVotes");
            // NB, totalVotes will be less than the group size due to message loss
            // thus in theory there can be more than one leader elected. however,
            // in that case each will 'kick out' the other leader swiftly, and
            // another leader will start
            if (positiveVotes * 2 > totalVotes) {
                System.err.println("I declare myself leader");
                // we have a majority, start leading
                leader = new Leader(group, middleware, currentTerm);
                middleware.getTimer().schedule(leader, Leader.HEARTBEAT_PERIOD, Leader.HEARTBEAT_PERIOD);
                middleware.sendGroup(new Leader.Heartbeat(System.currentTimeMillis(), currentTerm), false);
            }
             active = false;
        }

        public void onVoteReply(boolean reply, int term) {
            System.err.println("Vote reply");
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
        public final int version;
        public final long member;
        public Leave(int version, long member) {
            this.version = version;
            this.member  = member;
        }
    }

    public static class Join extends Message {
        /* Announce the join request */
        public final int version;
        public final long member;
        public final SocketAddress address;
        public Join(int version, long member, SocketAddress address) {
            this.version = version;
            this.member  = member;
            this.address = address;
        }
    }

    public static class VoteRequest extends Message {
        public final int term;
        public final int version;
        public VoteRequest(int term, int version) {
            this.term    = term;
            this.version = version;
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

    public void onHeartbeat(SocketAddress address, long timestamp, Leader.Heartbeat message) {
        // in theory, compute the difference between our received timestamp and
        // the leaders timestamp. In practice, just reply saying you're alive
        lastHeartbeat = timestamp;
        // cancel any running elections
        election.active = false;
        votedFor        = null;
        middleware.send(address, new Alive());
        if (message.term >= currentTerm) {
            if (group.getLeaderAddress() == null) {
                // this must be either our first leader, or our newly
                // elected leader.  so we can just accept it as our
                // leader. NB this can also be myself!
                group.setLeaderAddress(address);
            } else if (!group.getLeaderAddress().equals(address)) {
                // It very much looks as if we have a new leader.  If I
                // was the leader, I should stand down.
                if (leader != null) {
                    leader.cancel();
                    leader = null;
                }
                // set our current leader to null. If the leader that
                // sent us our earlier message is persistent, it will
                // re-establish itself. Otherwise, a new election will
                // have to take place.
                group.setLeaderAddress(null);
            }
        }
    }

    public void onJoin(Join request) {
        if (group.getVersion() + 1 == request.version) {
            group.add(request.member, request.address);
        } else {
            // hmm it's likely this request comes from a different leader.
            // (that should be the only reason it goes out of sync)
        }
    }

    public void onLeave(Leave request) {
        if (group.getVersion() + 1 == request.version) {
            group.remove(request.member);
        }
    }

    public void onWelcome(Leader.Welcome welcome) {
        this.pid = welcome.pid;
    }

    public long getPid() {
        return this.pid;
    }

    public void onVoteRequest(SocketAddress sender, int groupVersion, int term) {
        if (term >= currentTerm && groupVersion >= group.getVersion() && votedFor == null) {
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
            onHeartbeat(message.packet.getSocketAddress(), message.timestamp,
                        (Leader.Heartbeat)message.payload);
            break;
        }
        case VOTE_REQUEST: {
            VoteRequest req = (VoteRequest)message.payload;
            onVoteRequest(message.packet.getSocketAddress(), req.term, req.version);
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
