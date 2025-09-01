package sortx.core.data.parser;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ParserRegistry {
    private final List<DataParser> parsers = new ArrayList<>();

    public ParserRegistry() {
        parsers.add(new CsvDataParser());
        // Futuro: adicionar JSON, XML etc.
    }

    public DataParser findFor(String filename) {
        return parsers.stream().filter(p -> p.supports(filename)).findFirst().orElse(null);
    }

    public List<DataParser> all() { return parsers; }
}
