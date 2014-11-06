import java.util.Map;

/* Class to give each ordered message a unique sequence number */
class Sequencer {
    private Map<Integer, Integer> sequenceNrs;

    public Sequencer() {
        sequenceNrs = new DefaultHashMap<Integer, Integer>(0);
    }

    public synchronized int next(int pid) {
        int lastNr = sequenceNrs.get(pid);
        sequenceNrs.put(pid, ++lastNr);
        return lastNr;
    }
}
