import java.util.HashMap;

class DefaultHashMap<K,V> extends HashMap<K,V> {
    private V defaultValue;
    public DefaultHashMap(int capacity, V theDefaultValue) {
        super(capacity);
        this.defaultValue = theDefaultValue;
    }
}
