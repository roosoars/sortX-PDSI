package sortx.core.sort;

import java.util.Comparator;
import java.util.List;

public interface SortStrategy<T> {
    String name();
    void sort(List<T> list, Comparator<? super T> comparator);
}
