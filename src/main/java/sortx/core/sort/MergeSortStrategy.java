package sortx.core.sort;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Stable top-down Merge Sort implementation.
 *
 * How it works (divide and conquer):
 * 1) Divide the list into two halves.
 * 2) Sort each half recursively.
 * 3) Merge the two sorted halves back into the original list.
 *
 * Notes:
 * - Uses an auxiliary buffer with the same size as the input list (O(n) extra memory).
 * - Stable: equal elements preserve their relative order (when comparator returns 0).
 * - Time complexity: O(n log n) in the worst, average and best cases.
 */
public class MergeSortStrategy<T> implements SortStrategy<T> {
    @Override
    public String name() { return "MergeSort"; }

    @Override
    public void sort(List<T> list, Comparator<? super T> comparator) {
        if (list == null || list.size() < 2) return;
        // Auxiliary buffer initialized with current contents; we'll copy ranges into it during merge
        List<T> aux = new ArrayList<>(list);
        mergesort(list, aux, 0, list.size() - 1, comparator);
    }

    private void mergesort(List<T> a, List<T> aux, int lo, int hi, Comparator<? super T> comp) {
        if (lo >= hi) return;
        int mid = lo + (hi - lo) / 2;
        mergesort(a, aux, lo, mid, comp);
        mergesort(a, aux, mid + 1, hi, comp);
        // Small optimization: if already in order across the boundary, skip merge
        if (comp.compare(a.get(mid), a.get(mid + 1)) <= 0) return;
        merge(a, aux, lo, mid, hi, comp);
    }

    private void merge(List<T> a, List<T> aux, int lo, int mid, int hi, Comparator<? super T> comp) {
        // Copy current segment to auxiliary buffer
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
