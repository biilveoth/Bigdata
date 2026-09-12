package com.example.iplog;

import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Mapper;

import java.io.IOException;

public final class MaxIpMapper extends Mapper<LongWritable, Text, NullWritable, Text> {
    private final Text output = new Text();

    @Override
    protected void map(LongWritable key, Text value, Context context)
            throws IOException, InterruptedException {
        String line = value.toString().trim();
        if (line.isEmpty()) {
            return;
        }

        String[] fields = line.split("\\s+");
        if (fields.length != 2) {
            throw new IOException("Invalid IP count record: " + line);
        }

        try {
            long count = Long.parseLong(fields[1]);
            output.set(count + "\t" + fields[0]);
            context.write(NullWritable.get(), output);
        } catch (NumberFormatException exception) {
            throw new IOException("Invalid count in record: " + line, exception);
        }
    }
}