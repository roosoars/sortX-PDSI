package sortx.core.data;

import java.util.LinkedHashMap;
import java.util.Map;

public class DataRecord {
    private final Map<String, Object> values = new LinkedHashMap<>();

    public void put(String key, Object value) { values.put(key, value); }
    public Object get(String key) { return values.get(key); }
    public Map<String, Object> asMap() { return values; }
}
