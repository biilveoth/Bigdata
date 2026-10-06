package com.example.filter;

import java.io.IOException;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

public class OrderFilterMapper extends Mapper<LongWritable, Text, Text, Text> {
    static final String HEADER = "order_id,customer_id,order_date,total_amount,category,status";
    private FilterRules rules;

    @Override
    protected void setup(Context context) {
        rules = new FilterRules(context.getConfiguration());
    }

    @Override
    protected void map(LongWritable key, Text value, Context context)
            throws IOException, InterruptedException {
        String line = value.toString();
        if (line.trim().isEmpty()) {
            context.getCounter(FilterCounters.EMPTY_LINES).increment(1);
            return;
        }
        if (line.trim().equals(HEADER)) {
            context.getCounter(FilterCounters.HEADER_LINES).increment(1);
            return;
        }
        context.getCounter(FilterCounters.INPUT_RECORDS).increment(1);
        Order order;
        try {
            order = Order.parse(line);
        } catch (IllegalArgumentException | java.time.DateTimeException | IOException exception) {
            context.getCounter(FilterCounters.INVALID_RECORDS).increment(1);
            return;
        }
        if (rules.matches(order)) {
            context.write(new Text(order.customer), value);
            context.getCounter(FilterCounters.MAP_MATCHED).increment(1);
        } else {
            context.getCounter(FilterCounters.MAP_REJECTED).increment(1);
        }
    }
}
