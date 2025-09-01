package sortx.core.sort;

import java.util.Comparator;
import java.util.List;

public class SelectionSortStrategy<T> implements SortStrategy<T> {
    @Override
    public String name() { return "SelectionSort"; }

    @Override
    public void sort(List<T> list, Comparator<? super T> comparator) {
        if (list == null || list.size() < 2) return;
        int n = list.size();
        for (int i = 0; i < n - 1; i++) {
            int minIdx = i;
            for (int j = i + 1; j < n; j++) {
                if (comparator.compare(list.get(j), list.get(minIdx)) < 0) minIdx = j;
            }
            if (minIdx != i) {
                T tmp = list.get(i);
                list.set(i, list.get(minIdx));
                list.set(minIdx, tmp);
            }
        }
    }
}
