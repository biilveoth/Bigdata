package com.example.filter;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.util.VersionInfo;

public final class FilterDriver {
    private FilterDriver() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && args[0].equals("--help")) {
            System.out.println("Usage: java -jar conditional-filter-1.0.0.jar INPUT OUTPUT [options]");
            System.out.println("Options: --min-amount N --max-amount N --start-date YYYY-MM-DD"
                    + " --end-date YYYY-MM-DD --categories A,B --statuses A,B"
                    + " --min-orders N --min-total N");
            return;
        }
        if (args.length < 2 || (args.length - 2) % 2 != 0) {
            throw new IllegalArgumentException("Expected INPUT OUTPUT and option/value pairs; use --help");
        }
        Configuration conf = new Configuration();
        conf.set("mapreduce.framework.name", "local");
        conf.set("fs.defaultFS", "file:///");
        Set<String> allowed = new HashSet<>(Arrays.asList("min-amount", "max-amount",
                "start-date", "end-date", "categories", "statuses", "min-orders", "min-total"));
        Set<String> seen = new HashSet<>();
        for (int i = 2; i < args.length; i += 2) {
            String option = args[i];
            if (!option.startsWith("--") || !allowed.contains(option.substring(2))
                    || !seen.add(option)) throw new IllegalArgumentException("Unknown or repeated option: " + option);
            conf.set("filter." + option.substring(2), args[i + 1]);
        }
        new FilterRules(conf);
        Path input = new Path(args[0]);
        Path output = new Path(args[1]);
        try (FileSystem fs = input.getFileSystem(conf)) {
            if (!fs.exists(input)) throw new IllegalArgumentException("Input does not exist: " + input);
        }
        try (FileSystem fs = output.getFileSystem(conf)) {
            if (fs.exists(output)) throw new IllegalArgumentException("Output already exists: " + output);
        }
        Job job = Job.getInstance(conf, "Bai 20 - conditional order filter");
        job.setJarByClass(FilterDriver.class);
        job.setMapperClass(OrderFilterMapper.class);
        job.setReducerClass(CustomerFilterReducer.class);
        job.setMapOutputKeyClass(Text.class);
        job.setMapOutputValueClass(Text.class);
        job.setOutputKeyClass(NullWritable.class);
        job.setOutputValueClass(Text.class);
        job.setNumReduceTasks(1);
        FileInputFormat.addInputPath(job, input);
        FileOutputFormat.setOutputPath(job, output);
        long started = System.nanoTime();
        boolean success = job.waitForCompletion(true);
        long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
        for (FilterCounters counter : FilterCounters.values()) {
            System.out.println(counter + "=" + job.getCounters().findCounter(counter).getValue());
        }
        if (!success) throw new IllegalStateException("MapReduce job failed");
        // An underscore-prefixed sidecar is ignored by Hadoop input discovery.
        try (FileSystem fs = output.getFileSystem(conf);
                Writer writer = new OutputStreamWriter(fs.create(new Path(output, "_metrics.json"), false),
                        StandardCharsets.UTF_8)) {
            writer.write("{\n  \"hadoop_version\": \"" + VersionInfo.getVersion()
                    + "\",\n  \"job_elapsed_ms\": " + elapsedMillis + ",\n  \"counters\": {\n");
            FilterCounters[] counters = FilterCounters.values();
            for (int i = 0; i < counters.length; i++) {
                writer.write("    \"" + counters[i] + "\": "
                        + job.getCounters().findCounter(counters[i]).getValue()
                        + (i + 1 == counters.length ? "\n" : ",\n"));
            }
            writer.write("  }\n}\n");
        }
        System.out.println("JOB_ELAPSED_MS=" + elapsedMillis);
    }
}
