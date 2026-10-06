package com.example.filter;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Collections;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hadoop.conf.Configuration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class FilterTest {
    @TempDir Path temporary;

    @Test
    void inclusiveBoundariesAndQuotedCsv() throws Exception {
        FilterRules rules = new FilterRules(new Configuration(false));
        assertTrue(rules.matches(Order.parse("\"O,1\",KH1,2026-09-01,500000,Books,COMPLETED")));
        assertTrue(rules.matches(Order.parse("O2,KH1,2026-09-30,2000000,Electronics,COMPLETED")));
        assertFalse(rules.matches(Order.parse("O3,KH1,2026-10-01,1000000,Books,COMPLETED")));
        assertFalse(rules.matches(Order.parse("O4,KH1,2026-09-01,499999,Books,COMPLETED")));
        assertThrows(java.time.DateTimeException.class,
                () -> Order.parse("O5,KH1,2026-09-31,1000000,Books,COMPLETED"));
        for (String money : Arrays.asList("-1", "1e6", "1000000.001", "NaN")) {
            assertThrows(IllegalArgumentException.class,
                    () -> Order.parse("O6,KH1,2026-09-15," + money + ",Books,COMPLETED"));
        }
    }

    @Test
    void datesMatchPreviewAndOracleContract() throws Exception {
        for (String date : Arrays.asList("0000-09-01", "+10000-09-01", "2026-9-01")) {
            assertThrows(IllegalArgumentException.class,
                    () -> Order.parse("A,C," + date + ",1000000,Books,COMPLETED"));
            Configuration conf = new Configuration(false);
            conf.set("filter.start-date", date);
            assertThrows(IllegalArgumentException.class, () -> new FilterRules(conf));
        }
        assertNotNull(Order.parse("A,C,0001-01-01,1000000,Books,COMPLETED"));
        assertNotNull(Order.parse("A,C,9999-12-31,1000000,Books,COMPLETED"));
    }

    @Test
    void realMapReduceFiltersRecordsAndGroups() throws Exception {
        Path output = temporary.resolve("result");
        FilterDriver.main(new String[]{"data/orders.csv", output.toString()});
        assertEquals(new HashSet<>(Arrays.asList(
                "O001,KH01,2026-09-01,500000,Books,COMPLETED",
                "O002,KH01,2026-09-15,1000000,Electronics,COMPLETED",
                "O003,KH01,2026-09-30,1500000,Books,COMPLETED")),
                new HashSet<>(Files.readAllLines(output.resolve("part-r-00000"), StandardCharsets.UTF_8)));
        assertTrue(Files.exists(output.resolve("_SUCCESS")));
        assertThrows(IllegalArgumentException.class,
                () -> FilterDriver.main(new String[]{"data/orders.csv", output.toString()}));
    }

    @Test
    void invalidConditionsFailBeforeJobStarts() {
        assertThrows(IllegalArgumentException.class, () -> FilterDriver.main(new String[]{
                "data/orders.csv", temporary.resolve("unused").toString(),
                "--min-amount", "2000001"}));
        assertThrows(IllegalArgumentException.class, () -> FilterDriver.main(new String[]{
                "data/orders.csv", temporary.resolve("unused").toString(), "--min-orders", "0"}));
        assertThrows(IllegalArgumentException.class, () -> FilterDriver.main(new String[]{
                "data/orders.csv", temporary.resolve("unused").toString(), "--categories", "Books,"}));
        assertThrows(IllegalArgumentException.class, () -> FilterDriver.main(new String[]{
                "data/orders.csv", temporary.resolve("unused").toString(), "--unexpected", "1"}));
    }

    @Test
    void duplicateRowsAndExactDecimalGroupThreshold() throws Exception {
        Path output = temporary.resolve("edge");
        FilterDriver.main(new String[]{"data/orders-edge.csv", output.toString()});
        java.util.List<String> lines = Files.readAllLines(output.resolve("part-r-00000"), StandardCharsets.UTF_8);
        assertEquals(6, lines.size());
        assertEquals(2, Collections.frequency(lines,
                "E001,EDGE01,2026-09-01,500000,Books,COMPLETED"));
        assertTrue(lines.contains("\"E,003\",EDGE02,2026-09-15,1000000.10,Books,COMPLETED"));
        assertFalse(lines.stream().anyMatch(line -> line.contains("EDGE03")));
        JsonNode counters = new ObjectMapper().readTree(output.resolve("_metrics.json").toFile()).get("counters");
        assertEquals(8, counters.get("INVALID_RECORDS").asInt());
        assertEquals(1, counters.get("EMPTY_LINES").asInt());
        assertEquals(2, counters.get("MAP_REJECTED").asInt());
    }

    @Test
    void directoryInputAndNoMatchingCustomer() throws Exception {
        Path input = Files.createDirectory(temporary.resolve("input"));
        Files.write(input.resolve("a.csv"), Arrays.asList(OrderFilterMapper.HEADER,
                "A,K,2026-09-01,1000000,Books,COMPLETED"), StandardCharsets.UTF_8);
        Files.write(input.resolve("b.csv"), Arrays.asList(OrderFilterMapper.HEADER,
                "B,K,2026-09-01,1000000,Books,COMPLETED",
                "C,K,2026-09-01,1000000,Books,COMPLETED"), StandardCharsets.UTF_8);
        Path output = temporary.resolve("joined");
        FilterDriver.main(new String[]{input.toString(), output.toString()});
        assertEquals(3, Files.readAllLines(output.resolve("part-r-00000")).size());
        Path none = temporary.resolve("none");
        FilterDriver.main(new String[]{input.toString(), none.toString(), "--min-total", "3000000.01"});
        assertEquals(0, Files.size(none.resolve("part-r-00000")));
        assertTrue(Files.exists(none.resolve("_SUCCESS")));
    }
}
