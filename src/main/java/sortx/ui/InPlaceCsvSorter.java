package sortx.ui;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import sortx.core.data.DataRecord;
import sortx.core.rules.ComparatorFactory;
import sortx.core.rules.RuleSet;
import sortx.core.sort.SortStrategy;
import sortx.core.sort.SortStrategyRegistry;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;

public class InPlaceCsvSorter {

    public enum ExternalMode { INDEX, RUNS }

    private static final int MAX_LINE_BYTES = Integer.getInteger("sortx.maxLineBytes", 1_048_576); // 1MiB por linha (INDEX)
    private static final int INDEX_IO_BUFFER = Integer.getInteger("sortx.indexIoBuffer", 64 * 1024); // 64KiB
    private static final java.nio.charset.Charset CS = StandardCharsets.UTF_8;
    private static final int INDEX_REC_SIZE = 12; // [offset(long)=8][length(int)=4]

    private static final char CSV_DELIM = CSVFormat.DEFAULT.getDelimiter();

    private static final int MAX_ROWS_IN_MEMORY =
            Integer.getInteger("sortx.maxRowsInMemory", 50_000);


    public static void sortFile(File file,
                                RuleSet rules,
                                String algorithmName,
                                Locale locale,
                                SortStrategyRegistry sortRegistry) throws Exception {
        String prop = System.getProperty("sortx.externalMode", "INDEX");
        ExternalMode mode = "RUNS".equalsIgnoreCase(prop) ? ExternalMode.RUNS : ExternalMode.INDEX;
        sortFile(file, rules, algorithmName, locale, sortRegistry, mode);
    }

    public static void sortFile(File file,
                                RuleSet rules,
                                String algorithmName,
                                Locale locale,
                                SortStrategyRegistry sortRegistry,
                                ExternalMode mode) throws Exception {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(mode, "mode");

        if (mode == ExternalMode.INDEX) {
            // Modo 1 índice em disco (zero RAM)
            IndexInfo index = buildDiskIndex(file);
            Comparator<IndexEntry> cmp = buildDiskRecordComparator(file, index.headers, rules, locale);
            switch (algorithmName.toLowerCase(Locale.ROOT)) {
                case "mergesort" -> diskMergeSort(index.idxFile, cmp);
                case "quicksort" -> diskQuickSort(index.idxFile, 0, index.count - 1, cmp);
                case "bubblesort" -> diskBubbleSort(index.idxFile, index.count, cmp);
                case "selectionsort" -> diskSelectionSort(index.idxFile, index.count, cmp);
                default -> diskMergeSort(index.idxFile, cmp);
            }
            rewriteCsvFromIndex(file, index);
            try { index.idxFile.delete(); } catch (Exception ignored) {}
            return;
        }

        //  Modo 2 external merge por runs (chunks)
        externalMergeByRuns(file, rules, algorithmName, locale, sortRegistry);
    }

    // MODO 1 INDEX (zero RAM) lê/ordena índice no disco e regrava CSV pela ordem nova

    private static class IndexInfo {
        final File idxFile;
        final long headerStart;
        final int headerLength;
        final String newline;
        final String[] headers;
        final long count;
        IndexInfo(File idxFile, long headerStart, int headerLength, String newline, String[] headers, long count) {
            this.idxFile = idxFile;
            this.headerStart = headerStart;
            this.headerLength = headerLength;
            this.newline = newline;
            this.headers = headers;
            this.count = count;
        }
    }

    private static class IndexEntry {
        long offset;
        int length;
        IndexEntry() {}
        IndexEntry(long o, int l) { offset = o; length = l; }
    }

    private static IndexInfo buildDiskIndex(File csv) throws IOException {
        File idxFile = File.createTempFile("sortx_idx_", ".bin");

        try (InputStream in = new BufferedInputStream(new FileInputStream(csv), INDEX_IO_BUFFER);
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(idxFile), INDEX_IO_BUFFER))) {

            long pos = 0L;
            long lineStart = 0L;
            boolean sawCR = false;
            boolean firstLine = true;
            long rows = 0L;
            String newline = "\n";

            ByteArrayOutputStream headerCapture = new ByteArrayOutputStream(4096);

            int b;
            while ((b = in.read()) != -1) {
                if (b == '\n') {
                    long lineEndExclusive = pos;
                    int len = (int) (lineEndExclusive - lineStart);
                    if (sawCR && len > 0) { len -= 1; newline = "\r\n"; } else { newline = "\n"; }

                    if (firstLine) {
                        readRangeToBuffer(csv, lineStart, len, headerCapture);
                        firstLine = false;
                    } else {
                        out.writeLong(lineStart);
                        out.writeInt(len);
                        rows++;
                    }
                    lineStart = pos + 1;
                    sawCR = false;
                } else if (b == '\r') {
                    sawCR = true;
                } else {
                    sawCR = false;
                }
                pos++;
            }

            if (pos > lineStart) {
                int len = (int) (pos - lineStart);
                if (firstLine) {
                    readRangeToBuffer(csv, lineStart, len, headerCapture);
                    firstLine = false;
                } else {
                    out.writeLong(lineStart);
                    out.writeInt(len);
                    rows++;
                }
            }

            String headerLine = headerCapture.toString(CS);
            String[] headerNames = parseHeader(headerLine);

            return new IndexInfo(idxFile, 0L, headerLine.getBytes(CS).length, newline, headerNames, rows);
        }
    }

    private static void readRangeToBuffer(File csv, long offset, int length, ByteArrayOutputStream sink) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(csv, "r")) {
            raf.seek(offset);
            byte[] buf = new byte[Math.min(length, 64 * 1024)];
            int remaining = length;
            while (remaining > 0) {
                int toRead = Math.min(remaining, buf.length);
                int n = raf.read(buf, 0, toRead);
                if (n <= 0) break;
                sink.write(buf, 0, n);
                remaining -= n;
            }
        }
    }

    private static String[] parseHeader(String headerLine) throws IOException {
        try (CSVParser p = CSVParser.parse(headerLine, CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(false)
                .build())) {
            return p.getHeaderMap().keySet().toArray(new String[0]);
        }
    }

    private static Comparator<IndexEntry> buildDiskRecordComparator(File csvFile,
                                                                    String[] headers,
                                                                    RuleSet rules,
                                                                    Locale locale) {
        Comparator<DataRecord> dataComp = ComparatorFactory.build(rules, locale);
        return (a, b) -> {
            try {
                DataRecord ra = readDataRecord(csvFile, headers, a);
                DataRecord rb = readDataRecord(csvFile, headers, b);
                return dataComp.compare(ra, rb);
            } catch (IOException e) {
                return 0;
            }
        };
    }

    private static DataRecord readDataRecord(File csvFile, String[] headers, IndexEntry e) throws IOException {
        if (e.length > MAX_LINE_BYTES) {
            throw new IOException("Linha excede MAX_LINE_BYTES: " + e.length + " > " + MAX_LINE_BYTES);
        }
        byte[] bytes = new byte[e.length];
        try (RandomAccessFile raf = new RandomAccessFile(csvFile, "r")) {
            raf.seek(e.offset);
            raf.readFully(bytes);
        }
        String line = new String(bytes, CS);

        StringBuilder sb = new StringBuilder(headers.length * 8 + line.length() + 2);
        for (int i = 0; i < headers.length; i++) {
            if (i > 0) sb.append(CSV_DELIM);
            sb.append(headers[i]);
        }
        sb.append('\n').append(line);

        try (CSVParser p = CSVParser.parse(sb.toString(), CSVFormat.DEFAULT
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreSurroundingSpaces(true)
                .setTrim(true)
                .build())) {
            Iterator<CSVRecord> it = p.iterator();
            if (it.hasNext()) {
                CSVRecord rec = it.next();
                DataRecord dr = new DataRecord();
                for (String h : headers) dr.put(h, rec.get(h));
                return dr;
            } else {
                throw new IOException("Falha ao parsear linha em offset=" + e.offset);
            }
        }
    }

    private static long indexCount(File idxFile) {
        return idxFile.length() / INDEX_REC_SIZE;
    }

    private static IndexEntry readIndex(File idx, long i) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(idx, "r")) {
            raf.seek(i * INDEX_REC_SIZE);
            IndexEntry e = new IndexEntry();
            e.offset = raf.readLong();
            e.length = raf.readInt();
            return e;
        }
    }

    private static void writeIndex(File idx, long i, IndexEntry e) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(idx, "rw")) {
            raf.seek(i * INDEX_REC_SIZE);
            raf.writeLong(e.offset);
            raf.writeInt(e.length);
        }
    }

    private static void swapIndex(File idx, long i, long j) throws IOException {
        if (i == j) return;
        IndexEntry a = readIndex(idx, i);
        IndexEntry b = readIndex(idx, j);
        writeIndex(idx, i, b);
        writeIndex(idx, j, a);
    }

    private static IndexEntry readIndexAt(RandomAccessFile raf, long i) throws IOException {
        raf.seek(i * INDEX_REC_SIZE);
        IndexEntry e = new IndexEntry();
        e.offset = raf.readLong();
        e.length = raf.readInt();
        return e;
    }

    private static void writeIndexAt(RandomAccessFile raf, long i, IndexEntry e) throws IOException {
        raf.seek(i * INDEX_REC_SIZE);
        raf.writeLong(e.offset);
        raf.writeInt(e.length);
    }

    private static void swapIndexAt(RandomAccessFile raf, long i, long j) throws IOException {
        if (i == j) return;
        raf.seek(i * INDEX_REC_SIZE);
        long io = raf.readLong();
        int il = raf.readInt();

        raf.seek(j * INDEX_REC_SIZE);
        long jo = raf.readLong();
        int jl = raf.readInt();

        raf.seek(i * INDEX_REC_SIZE);
        raf.writeLong(jo);
        raf.writeInt(jl);

        raf.seek(j * INDEX_REC_SIZE);
        raf.writeLong(io);
        raf.writeInt(il);
    }

    private static void diskQuickSort(File idx, long lo, long hi, Comparator<IndexEntry> comp) throws IOException {
        if (lo >= hi) return;
        long i = lo, j = hi;
        IndexEntry pivot = readIndex(idx, lo + (hi - lo) / 2);
        while (i <= j) {
            while (compareBy(idx, i, pivot, comp) < 0) i++;
            while (compareBy(idx, j, pivot, comp) > 0) j--;
            if (i <= j) { swapIndex(idx, i, j); i++; j--; }
        }
        if (lo < j) diskQuickSort(idx, lo, j, comp);
        if (i < hi) diskQuickSort(idx, i, hi, comp);
    }

    private static int compareBy(File idx, long i, IndexEntry pivot, Comparator<IndexEntry> comp) throws IOException {
        IndexEntry e = readIndex(idx, i);
        return comp.compare(e, pivot);
    }

    private static void diskMergeSort(File idx, Comparator<IndexEntry> comp) throws IOException {
        long n = indexCount(idx);
        if (n <= 1) return;

        File aux = File.createTempFile("sortx_idx_aux_", ".bin");
        try (RandomAccessFile rafSrc = new RandomAccessFile(idx, "r");
             RandomAccessFile rafDst = new RandomAccessFile(aux, "rw")) {
            rafDst.setLength(rafSrc.length());
        }

        boolean srcIsIdx = true;
        for (long size = 1; size < n; size <<= 1) {
            File src = srcIsIdx ? idx : aux;
            File dst = srcIsIdx ? aux : idx;
            mergePass(src, dst, size, n, comp);
            srcIsIdx = !srcIsIdx;
        }

        if (!srcIsIdx) {
            Files.move(aux.toPath(), idx.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } else {
            try { aux.delete(); } catch (Exception ignored) {}
        }
    }

    private static void mergePass(File src, File dst, long size, long n, Comparator<IndexEntry> comp) throws IOException {
        try (RandomAccessFile in = new RandomAccessFile(src, "r");
             RandomAccessFile out = new RandomAccessFile(dst, "rw")) {
            long left = 0;
            while (left < n) {
                long mid = Math.min(left + size, n);
                long right = Math.min(left + 2 * size, n);
                long i = left, j = mid;
                while (i < mid && j < right) {
                    IndexEntry ei = readIndexAt(in, i);
                    IndexEntry ej = readIndexAt(in, j);
                    if (comp.compare(ei, ej) <= 0) {
                        writeIndexAt(out, left++, ei);
                        i++;
                    } else {
                        writeIndexAt(out, left++, ej);
                        j++;
                    }
                }
                while (i < mid) writeIndexAt(out, left++, readIndexAt(in, i++));
                while (j < right) writeIndexAt(out, left++, readIndexAt(in, j++));
            }
        }
    }

    private static void diskBubbleSort(File idx, long n, Comparator<IndexEntry> comp) throws IOException {
        boolean swapped;
        long end = n;
        try (RandomAccessFile raf = new RandomAccessFile(idx, "rw")) {
            do {
                swapped = false;
                for (long i = 1; i < end; i++) {
                    IndexEntry a = readIndexAt(raf, i - 1);
                    IndexEntry b = readIndexAt(raf, i);
                    if (comp.compare(a, b) > 0) {
                        swapIndexAt(raf, i - 1, i);
                        swapped = true;
                    }
                }
                end--;
            } while (swapped);
        }
    }

    private static void diskSelectionSort(File idx, long n, Comparator<IndexEntry> comp) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(idx, "rw")) {
            for (long i = 0; i < n - 1; i++) {
                long minIdx = i;
                IndexEntry min = readIndexAt(raf, i);
                for (long j = i + 1; j < n; j++) {
                    IndexEntry cur = readIndexAt(raf, j);
                    if (comp.compare(cur, min) < 0) {
                        minIdx = j;
                        min = cur;
                    }
                }
                if (minIdx != i) swapIndexAt(raf, i, minIdx);
            }
        }
    }

    private static void rewriteCsvFromIndex(File originalCsv, IndexInfo index) throws IOException {
        File out = File.createTempFile("sortx_out_", ".csv");

        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(out), 128 * 1024)) {
            writeHeader(originalCsv, bos, index.headerLength, index.newline);

            long n = indexCount(index.idxFile);
            try (RandomAccessFile rafIdx = new RandomAccessFile(index.idxFile, "r");
                 RandomAccessFile rafCsv = new RandomAccessFile(originalCsv, "r")) {

                byte[] lineBuf = new byte[Math.min(MAX_LINE_BYTES, 1 << 20)];
                for (long i = 0; i < n; i++) {
                    rafIdx.seek(i * INDEX_REC_SIZE);
                    long offset = rafIdx.readLong();
                    int length = rafIdx.readInt();

                    if (length > MAX_LINE_BYTES) throw new IOException("Linha excede MAX_LINE_BYTES: " + length);
                    if (lineBuf.length < length) lineBuf = new byte[length];

                    rafCsv.seek(offset);
                    rafCsv.readFully(lineBuf, 0, length);
                    bos.write(lineBuf, 0, length);
                    bos.write(index.newline.getBytes(CS));
                }
            }
        }

        try {
            Files.move(out.toPath(), originalCsv.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception atomicFail) {
            Files.move(out.toPath(), originalCsv.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void writeHeader(File csv, OutputStream out, int headerLength, String newline) throws IOException {
        try (RandomAccessFile raf = new RandomAccessFile(csv, "r")) {
            byte[] hdr = new byte[headerLength];
            raf.seek(0);
            raf.readFully(hdr);
            out.write(hdr);
            out.write(newline.getBytes(CS));
        }
    }

    // MODO 2 RUNS (chunks) external merge sort genérico por runs (usa pouca RAM)

    private static void externalMergeByRuns(File file,
                                            RuleSet rules,
                                            String algorithmName,
                                            Locale locale,
                                            SortStrategyRegistry sortRegistry) throws Exception {
        List<String> headers = readHeaders(file);

        Comparator<DataRecord> comparator = ComparatorFactory.build(rules, locale);
        @SuppressWarnings("unchecked")
        SortStrategy<Object> strategy = sortRegistry.byName(algorithmName);

        List<File> runs = new ArrayList<>();

        try (FileReader reader = new FileReader(file, CS);
             CSVParser parser = CSVFormat.DEFAULT
                     .builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreSurroundingSpaces(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {

            List<DataRecord> buffer = new ArrayList<>(Math.min(MAX_ROWS_IN_MEMORY, 10000));
            for (CSVRecord rec : parser) {
                DataRecord dr = new DataRecord();
                for (String h : headers) dr.put(h, rec.get(h));
                buffer.add(dr);
                if (buffer.size() >= MAX_ROWS_IN_MEMORY) {
                    runs.add(writeSortedRun(headers, buffer, comparator, strategy));
                    buffer.clear();
                }
            }
            if (!buffer.isEmpty()) {
                runs.add(writeSortedRun(headers, buffer, comparator, strategy));
                buffer.clear();
            }
        }

        if (runs.isEmpty()) {
            writeBackReplacing(file, headers, Collections.emptyList());
            return;
        }

        if (runs.size() == 1) {
            replaceFileKeepingName(file, runs.get(0));
            try { runs.get(0).delete(); } catch (Exception ignored) {}
            return;
        }

        File merged = mergeRuns(headers, runs, comparator);

        replaceFileKeepingName(file, merged);

        try { merged.delete(); } catch (Exception ignored) {}
        for (File run : runs) { try { run.delete(); } catch (Exception ignored) { } }
    }

    private static List<String> readHeaders(File file) throws IOException {
        try (FileReader reader = new FileReader(file, CS);
             CSVParser parser = CSVFormat.DEFAULT
                     .builder()
                     .setHeader()
                     .setSkipHeaderRecord(true)
                     .setIgnoreSurroundingSpaces(true)
                     .setTrim(true)
                     .build()
                     .parse(reader)) {
            return new ArrayList<>(parser.getHeaderMap().keySet());
        }
    }

    private static File writeSortedRun(List<String> headers,
                                       List<DataRecord> rows,
                                       Comparator<DataRecord> comparator,
                                       SortStrategy<Object> strategy) throws IOException {
        strategy.sort((List)(rows), (Comparator)comparator);

        File runFile = File.createTempFile("sortx_run_", ".csv");
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(runFile), CS));
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(headers.toArray(new String[0])).build())) {
            for (DataRecord r : rows) {
                List<String> values = new ArrayList<>(headers.size());
                for (String h : headers) {
                    Object v = r.asMap().get(h);
                    values.add(v == null ? "" : String.valueOf(v));
                }
                printer.printRecord(values);
            }
        }
        return runFile;
    }

    private static File mergeRuns(List<String> headers, List<File> runs, Comparator<DataRecord> comparator) throws IOException {
        class PQNode {
            final int runIndex;
            final DataRecord record;
            PQNode(int runIndex, DataRecord record) { this.runIndex = runIndex; this.record = record; }
        }
        List<CSVParser> parsers = new ArrayList<>();
        List<Iterator<CSVRecord>> iters = new ArrayList<>();
        try {
            for (File run : runs) {
                FileReader fr = new FileReader(run, CS);
                CSVParser parser = CSVFormat.DEFAULT
                        .builder()
                        .setHeader()
                        .setSkipHeaderRecord(true)
                        .setIgnoreSurroundingSpaces(true)
                        .setTrim(true)
                        .build()
                        .parse(fr);
                parsers.add(parser);
                iters.add(parser.iterator());
            }
            PriorityQueue<PQNode> pq = new PriorityQueue<>((a, b) -> comparator.compare(a.record, b.record));
            for (int i = 0; i < iters.size(); i++) {
                Iterator<CSVRecord> it = iters.get(i);
                if (it.hasNext()) {
                    CSVRecord rec = it.next();
                    DataRecord dr = new DataRecord();
                    for (String h : headers) dr.put(h, rec.get(h));
                    pq.add(new PQNode(i, dr));
                }
            }
            File out = File.createTempFile("sortx_merged_", ".csv");
            try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(out), CS));
                 CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(headers.toArray(new String[0])).build())) {
                while (!pq.isEmpty()) {
                    PQNode node = pq.poll();
                    List<String> values = new ArrayList<>(headers.size());
                    for (String h : headers) {
                        Object v = node.record.asMap().get(h);
                        values.add(v == null ? "" : String.valueOf(v));
                    }
                    printer.printRecord(values);
                    Iterator<CSVRecord> it = iters.get(node.runIndex);
                    if (it.hasNext()) {
                        CSVRecord rec = it.next();
                        DataRecord dr = new DataRecord();
                        for (String h : headers) dr.put(h, rec.get(h));
                        pq.add(new PQNode(node.runIndex, dr));
                    }
                }
            }
            return out;
        } finally {
            for (CSVParser p : parsers) {
                try { p.close(); } catch (Exception ignored) {}
            }
        }
    }

    private static void writeBackReplacing(File file, List<String> headers, List<DataRecord> rows) throws IOException {
        File temp = File.createTempFile("sortx_small_", ".csv");
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(temp), CS));
             CSVPrinter printer = new CSVPrinter(writer, CSVFormat.DEFAULT.builder().setHeader(headers.toArray(new String[0])).build())) {
            for (DataRecord r : rows) {
                List<String> values = new ArrayList<>(headers.size());
                for (String h : headers) {
                    Object v = r.asMap().get(h);
                    values.add(v == null ? "" : String.valueOf(v));
                }
                printer.printRecord(values);
            }
        }
        try {
            Files.move(temp.toPath(), file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception atomicFail) {
            Files.move(temp.toPath(), file.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void replaceFileKeepingName(File original, File newContent) throws IOException {
        try {
            Files.move(newContent.toPath(), original.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception atomicFail) {
            Files.move(newContent.toPath(), original.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
