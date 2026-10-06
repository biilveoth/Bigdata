package com.example.filter;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

public class CustomerFilterReducer extends Reducer<Text, Text, NullWritable, Text> {
    private FilterRules rules;

    @Override
    protected void setup(Context context) {
        rules = new FilterRules(context.getConfiguration());
    }

    @Override
    protected void reduce(Text customer, Iterable<Text> values, Context context)
            throws IOException, InterruptedException {
        long count = 0;
        BigDecimal total = BigDecimal.ZERO;
        // Spill the group until its aggregate predicate is known.
        java.nio.file.Path spool = Files.createTempFile("order-filter-", ".csv");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(spool, StandardCharsets.UTF_8)) {
                for (Text value : values) {
                    String line = value.toString();
                    total = total.add(Order.parse(line).amount);
                    count++;
                    writer.write(line);
                    writer.newLine();
                    context.progress();
                }
            }
            if (count >= rules.minOrders && total.compareTo(rules.minTotal) >= 0) {
                context.getCounter(FilterCounters.CUSTOMERS_ACCEPTED).increment(1);
                try (BufferedReader reader = Files.newBufferedReader(spool, StandardCharsets.UTF_8)) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        context.write(NullWritable.get(), new Text(line));
                        context.getCounter(FilterCounters.OUTPUT_RECORDS).increment(1);
                    }
                }
            } else {
                context.getCounter(FilterCounters.CUSTOMERS_REJECTED).increment(1);
                context.getCounter(FilterCounters.GROUP_REJECTED_RECORDS).increment(count);
            }
        } finally {
            Files.deleteIfExists(spool);
        }
    }
}
