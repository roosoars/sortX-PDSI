package sortx.core.rules;

import sortx.core.data.DataRecord;

import java.text.Collator;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.Locale;

public class ComparatorFactory {

    public static Comparator<DataRecord> build(RuleSet ruleSet, Locale locale) {
        Comparator<DataRecord> finalComp = (a, b) -> 0;
        for (SortRule rule : ruleSet.all()) {
            Comparator<DataRecord> c = comparatorFor(rule, locale);
            finalComp = finalComp.thenComparing(c);
        }
        return finalComp;
    }

    private static Comparator<DataRecord> comparatorFor(SortRule rule, Locale locale) {
        Comparator<Object> base;
        switch (rule.getType()) {
            case NUMBER -> base = ComparatorFactory::compareAsNumber;
            case DATE -> base = ComparatorFactory::compareAsDate;
            case BOOLEAN -> base = ComparatorFactory::compareAsBoolean;
            default -> {
                Collator collator = Collator.getInstance(locale);
                collator.setStrength(rule.isCaseInsensitive() ? Collator.PRIMARY : Collator.TERTIARY);
                base = (x, y) -> compareAsString(x, y, collator);
            }
        }

        Comparator<Object> withNulls = Comparator.nullsLast(base);
        Comparator<DataRecord> comp = Comparator.comparing(dr -> dr.asMap().get(rule.getColumn()), withNulls);
        if (rule.getOrder() == SortOrder.DESC) {
            comp = comp.reversed();
        }
        return comp;
    }

    private static int compareAsString(Object a, Object b, Collator collator) {
        String sa = a == null ? null : String.valueOf(a);
        String sb = b == null ? null : String.valueOf(b);
        if (sa == null && sb == null) return 0;
        if (sa == null) return -1;
        if (sb == null) return 1;
        return collator.compare(sa, sb);
    }

    private static int compareAsNumber(Object a, Object b) {
        Double da = toDouble(a);
        Double db = toDouble(b);
        return Comparator.<Double>nullsLast(Double::compareTo).compare(da, db);
    }

    private static Double toDouble(Object o) {
        if (o == null) return null;
        if (o instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(o.toString().replace(",", ".")); }
        catch (Exception e) { return null; }
    }

    private static int compareAsDate(Object a, Object b) {
        LocalDate da = toDate(a);
        LocalDate db = toDate(b);
        if (da == null && db == null) return 0;
        if (da == null) return -1;
        if (db == null) return 1;
        return da.compareTo(db);
    }

    private static LocalDate toDate(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        DateTimeFormatter[] fmts = new DateTimeFormatter[] {
                DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("dd/MM/yyyy"),
                DateTimeFormatter.ofPattern("MM/dd/yyyy")
        };
        for (DateTimeFormatter f : fmts) {
            try { return LocalDate.parse(s, f); } catch (Exception ignored) {}
        }
        return null;
    }

    private static int compareAsBoolean(Object a, Object b) {
        Boolean ba = toBoolean(a);
        Boolean bb = toBoolean(b);
        if (ba == null && bb == null) return 0;
        if (ba == null) return -1;
        if (bb == null) return 1;
        return Boolean.compare(ba, bb);
    }

    private static Boolean toBoolean(Object o) {
        if (o == null) return null;
        if (o instanceof Boolean b) return b;
        String s = o.toString().trim().toLowerCase();
        return switch (s) {
            case "true", "t", "yes", "y", "1", "sim" -> true;
            case "false", "f", "no", "n", "0", "nao", "não" -> false;
            default -> null;
        };
    }
}
