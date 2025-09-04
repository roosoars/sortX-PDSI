package sortx.core.sort;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MergeSortStrategy<T> implements SortStrategy<T> {
    @Override
    public String name() { return "MergeSort"; }

    @Override
    public void sort(List<T> list, Comparator<? super T> comparator) {
        if (list == null || list.size() < 2) return;
        List<T> aux = new ArrayList<>(list);
        mergesort(list, aux, 0, list.size() - 1, comparator);
    }

    private void mergesort(List<T> a, List<T> aux, int lo, int hi, Comparator<? super T> comp) {
        if (lo >= hi) return;
        int mid = lo + (hi - lo) / 2;
        mergesort(a, aux, lo, mid, comp);
        mergesort(a, aux, mid + 1, hi, comp);
        if (comp.compare(a.get(mid), a.get(mid + 1)) <= 0) return;
        merge(a, aux, lo, mid, hi, comp);
    }

    private void merge(List<T> a, List<T> aux, int lo, int mid, int hi, Comparator<? super T> comp) {
        for (int k = lo; k <= hi; k++) aux.set(k, a.get(k));
        int i = lo, j = mid + 1;
        for (int k = lo; k <= hi; k++) {
            if (i > mid) {
                a.set(k, aux.get(j++));
            } else if (j > hi) {
                a.set(k, aux.get(i++));
            } else if (comp.compare(aux.get(j), aux.get(i)) < 0) {
                a.set(k, aux.get(j++));
            } else {
                a.set(k, aux.get(i++));
            }
        }
    }
}
