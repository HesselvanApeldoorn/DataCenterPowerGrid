import java.util.Map;

/* Class to give each ordered message a unique sequence number */
class Sequencer {
    private Map<Long, Integer> sequenceNrs;

    public Sequencer() {
        sequenceNrs = new DefaultHashMap<Long, Integer>(0);
    }

    public synchronized int next(long pid) {
        int lastNr = sequenceNrs.get(pid);
        sequenceNrs.put(pid, ++lastNr);
        return lastNr;
    }
}
