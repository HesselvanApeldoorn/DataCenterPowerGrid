import java.net.SocketAddress;
import java.util.TimerTask;
import java.util.Timer;
import java.util.Random;

class Member {
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
            currentTerm += 1;
            totalVotes = 0;
            positiveVotes = 0;
            active = true;
            middleware.sendGroup(new VoteRequest(currentTerm, group.getVersion()), false);
        }

        private void countVotes() {
            // NB, totalVotes will be less than the group size due to message loss
            // thus in theory there can be more than one leader elected. however,
            // in that case each will 'kick out' the other leader swiftly, and
            // another leader will start
            if (positiveVotes * 2 > totalVotes) {
                // we have a majority, start leading
                leader = new Leader(group, middleware, currentTerm);
                middleware.getTimer().schedule(leader, Leader.HEARTBEAT_PERIOD, Leader.HEARTBEAT_PERIOD);
                middleware.sendGroup(new Leader.Heartbeat(System.currentTimeMillis(), currentTerm), false);
            }
             active = false;
        }

        public void onVoteReply(SocketAddress sender, boolean reply, int term) {
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
        if (group.getLeaderAddress() == null && message.term >= currentTerm) {
            // this must be either our first leader, or our newly
            // elected leader.  so we can just accept it as our
            // leader. NB this can also be myself!
            group.setLeaderAddress(address);
        } else if (!group.getLeaderAddress().equals(address) && message.term >= currentTerm) {
            // It very much looks as if we have a new leader.  If I
            // was the leader, I should stand down.
            if (leader != null) {
                leader.cancel();
                leader = null;
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

}
