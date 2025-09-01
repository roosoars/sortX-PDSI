package sortx.core.rules;

import sortx.core.data.DataRecord;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class TypeInference {

    private static String norm(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase().trim();
    }

    public static ColumnType inferForColumn(List<sortx.core.data.DataRecord> rows, String column) {
        int samples = Math.min(50, rows.size());
        int booleanCount = 0, numberCount = 0, dateCount = 0, stringCount = 0;
        for (int i = 0; i < samples; i++) {
            DataRecord r = rows.get(i);
            Object v = r.asMap().get(column);
            if (v == null) continue;
            if (isBoolean(v)) { booleanCount++; continue; }
            if (isNumber(v)) { numberCount++; continue; }
            if (isDate(v)) { dateCount++; continue; }
            stringCount++;
        }
        double thr = samples * 0.7; // 70% para tipar como específico
        if (booleanCount >= thr) return ColumnType.BOOLEAN;
        if (numberCount  >= thr) return ColumnType.NUMBER;
        if (dateCount    >= thr) return ColumnType.DATE;
        return ColumnType.STRING;
    }

    private static boolean isBoolean(Object o) {
        String s = norm(o.toString());
        return s.equals("true") || s.equals("false") || s.equals("t") || s.equals("f") ||
                s.equals("yes") || s.equals("no") || s.equals("y") || s.equals("n") ||
                s.equals("1") || s.equals("0") || s.equals("sim") || s.equals("nao");
    }

    private static boolean isNumber(Object o) {
        try { Double.parseDouble(o.toString().replace(",", ".")); return true; } catch (Exception e) { return false; }
    }

    private static boolean isDate(Object o) {
        String s = o.toString().trim();
        DateTimeFormatter[] fmts = new DateTimeFormatter[] {
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("MM/dd/yyyy")
        };
        for (DateTimeFormatter f : fmts) {
            try { LocalDate.parse(s, f); return true; } catch (Exception ignored) {}
        }
        return false;
    }
}
