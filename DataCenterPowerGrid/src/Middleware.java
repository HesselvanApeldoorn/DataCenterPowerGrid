// The idea is that we use the strategy that was first made popular by the
// 'horus' system as well as in the jgroups library. That is to say, to arrange
// middleware in layers, each of which does only a simple thing (or relatively
// simple at any rate). But that obviously strains API design a bit.
// This is not a good API. I will change it. (Or someone else may :-))
interface Middleware {
    public Message onReceive(Message msg);
    public Message onSend(Message msg);
}
