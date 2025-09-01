package sortx.core.data;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DataSet {
    private final List<String> headers = new ArrayList<>();
    private final List<DataRecord> rows = new ArrayList<>();

    public List<String> getHeaders() { return headers; }
    public List<DataRecord> getRows() { return rows; }

    public void addHeader(String h) { headers.add(h); }
    public void addRow(DataRecord r) { rows.add(r); }

    public boolean isEmpty() { return rows.isEmpty(); }

    public List<DataRecord> snapshot() { return new ArrayList<>(rows); }

    public void replaceAll(List<DataRecord> sorted) {
        rows.clear();
        rows.addAll(sorted);
    }

    public List<String> immutableHeaders() { return Collections.unmodifiableList(headers); }
}
