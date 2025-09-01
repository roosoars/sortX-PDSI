package sortx.core.service;

import org.springframework.stereotype.Service;
import sortx.core.data.DataRecord;
import sortx.core.data.DataSet;
import sortx.core.rules.ComparatorFactory;
import sortx.core.rules.RuleSet;
import sortx.core.sort.SortStrategy;
import sortx.core.sort.SortStrategyRegistry;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class SortingService {
    private final SortStrategyRegistry registry;

    public SortingService(SortStrategyRegistry registry) {
        this.registry = registry;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public void sort(DataSet dataSet, RuleSet rules, String algorithmName, Locale locale) {
        List<DataRecord> working = dataSet.snapshot();
        Comparator<DataRecord> comparator = ComparatorFactory.build(rules, locale);
        SortStrategy<Object> strategy = registry.byName(algorithmName);
        strategy.sort((List)(working), (Comparator)comparator);
        dataSet.replaceAll(working);
    }
}
