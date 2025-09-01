package sortx.core.sort;

import java.util.Comparator;
import java.util.List;

public class QuickSortStrategy<T> implements SortStrategy<T> {

    @Override
    public String name() { return "QuickSort"; }

    @Override
    public void sort(List<T> list, Comparator<? super T> comparator) {
        if (list == null || list.size() < 2) return;
        quicksort(list, 0, list.size() - 1, comparator);
    }

    private void quicksort(List<T> a, int low, int high, Comparator<? super T> comp) {
        int i = low, j = high;
        T pivot = a.get(low + (high - low) / 2);
        while (i <= j) {
            while (comp.compare(a.get(i), pivot) < 0) i++;
            while (comp.compare(a.get(j), pivot) > 0) j--;
            if (i <= j) {
                swap(a, i, j);
                i++; j--;
            }
        }
        if (low < j) quicksort(a, low, j, comp);
        if (i < high) quicksort(a, i, high, comp);
    }

    private void swap(List<T> a, int i, int j) {
        T tmp = a.get(i);
        a.set(i, a.get(j));
        a.set(j, tmp);
    }
}
