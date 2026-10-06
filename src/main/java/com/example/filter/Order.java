package com.example.filter;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

final class Order {
    final String customer;
    final LocalDate date;
    final BigDecimal amount;
    final String category;
    final String status;

    private Order(CSVRecord row) {
        if (row.size() != 6) throw new IllegalArgumentException("Expected six fields");
        for (String field : row) {
            if (field.trim().isEmpty()) throw new IllegalArgumentException("Empty field");
        }
        customer = row.get(1).trim();
        date = parseDate(row.get(2).trim());
        String money = row.get(3).trim();
        if (!money.matches("[0-9]+(?:\\.[0-9]{1,2})?")) {
            throw new IllegalArgumentException("Expected nonnegative plain money, up to two decimals");
        }
        amount = new BigDecimal(money);
        category = row.get(4).trim();
        status = row.get(5).trim();
    }

    static Order parse(String line) throws IOException {
        try (CSVParser parser = CSVFormat.DEFAULT.parse(new StringReader(line))) {
            List<CSVRecord> rows = parser.getRecords();
            if (rows.size() != 1) throw new IllegalArgumentException("Expected one record");
            return new Order(rows.get(0));
        } catch (java.io.UncheckedIOException exception) {
            throw exception.getCause();
        }
    }

    static LocalDate parseDate(String value) {
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}") || value.startsWith("0000-")) {
            throw new IllegalArgumentException("Expected YYYY-MM-DD with year 0001 through 9999");
        }
        return LocalDate.parse(value);
    }
}
