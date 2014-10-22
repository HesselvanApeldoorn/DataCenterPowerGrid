import java.util.HashMap;

class DefaultHashMap<K,V> extends HashMap<K,V> {
    private V defaultValue;
    public DefaultHashMap(V theDefaultValue) {
        super();
        this.defaultValue = theDefaultValue;
    }

    public DefaultHashMap(int capacity, V theDefaultValue) {
        super(capacity);
        this.defaultValue = theDefaultValue;
    }

    @Override
    public V get(Object key) {
        return containsKey(key) ? super.get(key) : defaultValue;
    }
}
