package sortx.core.data.parser;

import sortx.core.data.DataSet;

import java.io.File;

public interface DataParser {
    boolean supports(String filename);
    DataSet parse(File file) throws Exception;
    String name();
}
