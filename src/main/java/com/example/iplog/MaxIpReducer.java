package com.example.iplog;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MaxIpReducer extends Reducer<NullWritable, Text, Text, LongWritable> {
    @Override
    protected void reduce(NullWritable ignored, Iterable<Text> records, Context context)
            throws IOException, InterruptedException {
        long maximum = Long.MIN_VALUE;
        List<String> ips = new ArrayList<>();

        for (Text record : records) {
            String[] fields = record.toString().split("\\t", 2);
            long count = Long.parseLong(fields[0]);
            String ip = fields[1];

            if (count > maximum) {
                maximum = count;
                ips.clear();
                ips.add(ip);
            } else if (count == maximum) {
                ips.add(ip);
            }
        }

        Collections.sort(ips);
        LongWritable outputCount = new LongWritable(maximum);
        for (String ip : ips) {
            context.write(new Text(ip), outputCount);
        }
    }
}
