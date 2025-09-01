package sortx.core.stats;

import org.springframework.stereotype.Component;
import sortx.core.data.DataRecord;

import java.util.*;

@Component
public class StatsService {
    public record NumericStats(long count, double min, double max, double mean, double median, double stddev) {}

    public NumericStats computeForNumeric(List<DataRecord> rows, String column) {
        List<Double> vals = new ArrayList<>();
        for (DataRecord r : rows) {
            Object o = r.asMap().get(column);
            if (o == null) continue;
            try { vals.add(Double.parseDouble(o.toString().replace(",", "."))); } catch (Exception ignored) {}
        }
        if (vals.isEmpty()) return new NumericStats(0, Double.NaN, Double.NaN, Double.NaN, Double.NaN, Double.NaN);
        Collections.sort(vals);
        long n = vals.size();
        double min = vals.get(0);
        double max = vals.get(vals.size() - 1);
        double sum = 0.0;
        for (double v : vals) sum += v;
        double mean = sum / n;
        double median = (n % 2 == 0) ? (vals.get((int)n/2 - 1) + vals.get((int)n/2)) / 2.0 : vals.get((int)n/2);
        double variance = 0.0;
        for (double v : vals) variance += Math.pow(v - mean, 2);
        variance /= n;
        double stddev = Math.sqrt(variance);
        return new NumericStats(n, min, max, mean, median, stddev);
    }

    public Map<String, Long> frequency(List<DataRecord> rows, String column, int topN) {
        Map<String, Long> freq = new HashMap<>();
        for (DataRecord r : rows) {
            Object o = r.asMap().get(column);
            String k = String.valueOf(o);
            freq.put(k, freq.getOrDefault(k, 0L) + 1);
        }
        return freq.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(topN)
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (x, y) -> x,
                        LinkedHashMap::new
                ));
    }
}
