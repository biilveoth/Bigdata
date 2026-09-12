package com.example.iplog;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Reducer;

import java.io.IOException;

public final class LongSumReducer extends Reducer<Text, LongWritable, Text, LongWritable> {
    private final LongWritable outputCount = new LongWritable();

    @Override
    protected void reduce(Text ip, Iterable<LongWritable> counts, Context context)
            throws IOException, InterruptedException {
        long sum = 0L;
        for (LongWritable count : counts) {
            sum += count.get();
        }
        outputCount.set(sum);
        context.write(ip, outputCount);
    }
}
