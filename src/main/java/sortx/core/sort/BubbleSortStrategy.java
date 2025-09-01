package sortx.core.sort;

import java.util.Comparator;
import java.util.List;

public class BubbleSortStrategy<T> implements SortStrategy<T> {
    @Override
    public String name() { return "BubbleSort"; }

    @Override
    public void sort(List<T> list, Comparator<? super T> comparator) {
        if (list == null || list.size() < 2) return;
        boolean swapped;
        int n = list.size();
        do {
            swapped = false;
            for (int i = 1; i < n; i++) {
                if (comparator.compare(list.get(i - 1), list.get(i)) > 0) {
                    T tmp = list.get(i - 1);
                    list.set(i - 1, list.get(i));
                    list.set(i, tmp);
                    swapped = true;
                }
            }
            n--; // last element is in place
        } while (swapped);
    }
}
