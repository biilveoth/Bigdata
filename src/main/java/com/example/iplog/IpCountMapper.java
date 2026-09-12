package com.example.iplog;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

import java.io.IOException;
import java.util.Optional;

public final class IpCountMapper extends Mapper<LongWritable, Text, Text, LongWritable> {
    private static final LongWritable ONE = new LongWritable(1L);
    private final Text outputIp = new Text();

    @Override
    protected void map(LongWritable key, Text value, Context context)
            throws IOException, InterruptedException {
        context.getCounter(LogCounters.TOTAL_LINES).increment(1L);

        String line = value.toString();
        if (line.trim().isEmpty()) {
            context.getCounter(LogCounters.EMPTY_LINES).increment(1L);
            return;
        }

        Optional<String> ip = LogParser.extractIp(line);
        if (!ip.isPresent()) {
            context.getCounter(LogCounters.INVALID_LINES).increment(1L);
            return;
        }

        context.getCounter(LogCounters.VALID_LINES).increment(1L);
        outputIp.set(ip.get());
        context.write(outputIp, ONE);
    }
}