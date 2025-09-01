package sortx.core.sort;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SortStrategyRegistry {
    private final List<SortStrategy<?>> strategies = new ArrayList<>();

    public SortStrategyRegistry() {
        strategies.add(new QuickSortStrategy<>());
        strategies.add(new MergeSortStrategy<>());
        strategies.add(new BubbleSortStrategy<>());
        strategies.add(new SelectionSortStrategy<>());
    }

    @SuppressWarnings("unchecked")
    public SortStrategy<Object> byName(String name) {
        return (SortStrategy<Object>) strategies.stream()
                .filter(s -> s.name().equalsIgnoreCase(name))
                .findFirst()
                .orElse((SortStrategy<Object>) strategies.get(0));
    }

    public List<SortStrategy<?>> all() { return strategies; }
}
