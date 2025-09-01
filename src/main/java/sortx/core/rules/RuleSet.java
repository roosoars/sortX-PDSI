package sortx.core.rules;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RuleSet {
    private final List<SortRule> rules = new ArrayList<>();

    public void add(SortRule r) { rules.add(r); }
    public void remove(SortRule r) { rules.remove(r); }
    public List<SortRule> all() { return Collections.unmodifiableList(rules); }
    public void clear() { rules.clear(); }
    public boolean isEmpty() { return rules.isEmpty(); }
}
