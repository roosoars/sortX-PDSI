package sortx.core.data.parser;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import sortx.core.data.DataRecord;
import sortx.core.data.DataSet;

import java.io.File;
import java.io.FileReader;
import java.nio.charset.StandardCharsets;

public class CsvDataParser implements DataParser {
    @Override
    public boolean supports(String filename) {
        String lower = filename.toLowerCase();
        return lower.endsWith(".csv");
    }

    @Override
    public DataSet parse(File file) throws Exception {
        DataSet ds = new DataSet();
        try (FileReader reader = new FileReader(file, StandardCharsets.UTF_8);
             CSVParser parser = CSVFormat.DEFAULT
                     .builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreSurroundingSpaces(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            ds.getHeaders().addAll(parser.getHeaderMap().keySet());

            for (CSVRecord rec : parser) {
                DataRecord dr = new DataRecord();
                for (String h : ds.getHeaders()) {
                    dr.put(h, rec.get(h));
                }
                ds.addRow(dr);
            }
        }
        return ds;
    }

    @Override
    public String name() { return "CSV"; }
}
