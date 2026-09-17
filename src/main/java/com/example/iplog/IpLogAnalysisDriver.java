package com.example.iplog;

import com.example.hdfs.HdfsFileLister;
import com.example.hdfs.LdapAuthenticator;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.JobStatus;
import org.apache.hadoop.mapreduce.TaskCompletionEvent;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.TextOutputFormat;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;

public final class IpLogAnalysisDriver
        extends Configured
        implements Tool {

    private static final String DEFAULT_HDFS_URI =
            "webhdfs://171.226.11.41:30093";

    public static void main(String[] args) throws Exception {
        configureWindowsHadoopNativeLibrary();

        Configuration configuration =
                new Configuration();

        loadHadoopConfiguration(configuration);

        int exitCode =
                ToolRunner.run(
                        configuration,
                        new IpLogAnalysisDriver(),
                        args
                );

        System.exit(exitCode);
    }

    @Override
    public int run(String[] args) throws Exception {
        LdapAuthenticator.authenticateFromEnvironment();
        Arguments arguments;

        try {
            arguments =
                    Arguments.parse(args);
        } catch (IllegalArgumentException exception) {
            System.err.println(
                    exception.getMessage()
            );

            printUsage();
            return 2;
        }

        Configuration configuration =
                getConf();

        if (configuration == null) {
            configuration =
                    new Configuration();

            setConf(configuration);
        }

        /*
         * Cấu hình client trước khi gọi FileSystem.newInstance()
         * hoặc tạo Job MapReduce.
         */
        HdfsFileLister.configureFileSystemClient(
                configuration,
                arguments.hdfsUri
        );

        /*
         * Mapper và Reducer chạy trong JVM trên máy local.
         */
        configuration.set(
                "mapreduce.framework.name",
                "local"
        );

        configuration.setBoolean(
                "mapreduce.app-submission.cross-platform",
                true
        );

        Path input =
                new Path(arguments.inputPath);

        Path output =
                new Path(arguments.outputPath);

        Path intermediate =
                new Path(
                        arguments.outputPath
                                + "_ip_counts"
                );

        URI hdfsUri =
                URI.create(arguments.hdfsUri);

        try (FileSystem fileSystem =
                     FileSystem.newInstance(
                             hdfsUri,
                             configuration
                     )) {

            validatePaths(
                    fileSystem,
                    input,
                    intermediate,
                    output,
                    arguments.overwrite
            );

            System.out.printf(
                    "Connected to %s%n",
                    fileSystem.getUri()
            );

            System.out.printf(
                    "FileSystem implementation: %s%n",
                    fileSystem.getClass().getName()
            );

            System.out.printf(
                    "Input: %s%n",
                    input.makeQualified(
                            fileSystem.getUri(),
                            fileSystem.getWorkingDirectory()
                    )
            );

            System.out.printf(
                    "Intermediate output: %s%n",
                    intermediate
            );

            System.out.printf(
                    "Final output: %s%n",
                    output
            );

            /*
             * Job 1:
             * access.log -> IP -> tổng số lần xuất hiện.
             */
            Job countJob =
                    createCountJob(
                            input,
                            intermediate
                    );

            if (!countJob.waitForCompletion(true)) {
                System.err.println(
                        "Job 1 (IP count) failed."
                );

                printFailureDiagnostics(countJob);
                return 1;
            }

            printCounters(countJob);

            long validLines =
                    countJob.getCounters()
                            .findCounter(
                                    LogCounters.VALID_LINES
                            )
                            .getValue();

            if (validLines == 0L) {
                System.err.println(
                        "No valid IP records were found; "
                                + "Job 2 was not started."
                );

                return 3;
            }

            /*
             * Job 2:
             * danh sách IP và count -> IP có count lớn nhất.
             */
            Job maxJob =
                    createMaxJob(
                            intermediate,
                            output
                    );

            if (!maxJob.waitForCompletion(true)) {
                System.err.println(
                        "Job 2 (maximum IP) failed."
                );

                printFailureDiagnostics(maxJob);
                return 1;
            }

            System.out.printf(
                    "Analysis completed. "
                            + "Result: %s/part-r-00000%n",
                    output
            );

            return 0;
        }
    }

    private Job createCountJob(
            Path input,
            Path output
    ) throws Exception {

        Job job =
                Job.getInstance(
                        getConf(),
                        "ip-log-count"
                );

        job.setJarByClass(
                IpLogAnalysisDriver.class
        );

        job.setInputFormatClass(
                TextInputFormat.class
        );

        job.setMapperClass(
                IpCountMapper.class
        );

        job.setCombinerClass(
                LongSumReducer.class
        );

        job.setReducerClass(
                LongSumReducer.class
        );

        job.setMapOutputKeyClass(
                Text.class
        );

        job.setMapOutputValueClass(
                LongWritable.class
        );

        job.setOutputKeyClass(
                Text.class
        );

        job.setOutputValueClass(
                LongWritable.class
        );

        job.setOutputFormatClass(
                TextOutputFormat.class
        );

        FileInputFormat.addInputPath(
                job,
                input
        );

        FileOutputFormat.setOutputPath(
                job,
                output
        );

        return job;
    }

    private Job createMaxJob(
            Path input,
            Path output
    ) throws Exception {

        Job job =
                Job.getInstance(
                        getConf(),
                        "ip-log-maximum"
                );

        job.setJarByClass(
                IpLogAnalysisDriver.class
        );

        job.setInputFormatClass(
                TextInputFormat.class
        );

        job.setMapperClass(
                MaxIpMapper.class
        );

        job.setReducerClass(
                MaxIpReducer.class
        );

        /*
         * Chỉ dùng một reducer để bảo đảm tạo ra
         * đúng một IP có số lần truy cập lớn nhất.
         */
        job.setNumReduceTasks(1);

        job.setMapOutputKeyClass(
                NullWritable.class
        );

        job.setMapOutputValueClass(
                Text.class
        );

        job.setOutputKeyClass(
                Text.class
        );

        job.setOutputValueClass(
                LongWritable.class
        );

        job.setOutputFormatClass(
                TextOutputFormat.class
        );

        FileInputFormat.addInputPath(
                job,
                input
        );

        FileOutputFormat.setOutputPath(
                job,
                output
        );

        return job;
    }

    private static void validatePaths(
            FileSystem fileSystem,
            Path input,
            Path intermediate,
            Path output,
            boolean overwrite
    ) throws Exception {

        if (!fileSystem.exists(input)) {
            throw new IllegalArgumentException(
                    "Input path does not exist: "
                            + input
            );
        }

        if (overwrite) {
            deleteOutput(
                    fileSystem,
                    intermediate
            );

            deleteOutput(
                    fileSystem,
                    output
            );

            return;
        }

        if (fileSystem.exists(intermediate)) {
            throw new IllegalArgumentException(
                    "Intermediate output already exists: "
                            + intermediate
                            + ". Use --overwrite "
                            + "or choose another output."
            );
        }

        if (fileSystem.exists(output)) {
            throw new IllegalArgumentException(
                    "Output already exists: "
                            + output
                            + ". Use --overwrite "
                            + "or choose another output."
            );
        }
    }

    private static void deleteOutput(
            FileSystem fileSystem,
            Path path
    ) throws Exception {

        if (fileSystem.exists(path)
                && !fileSystem.delete(path, true)) {
            throw new IllegalStateException(
                    "Cannot delete existing output: "
                            + path
            );
        }
    }

    private static void printCounters(
            Job job
    ) throws Exception {

        System.out.println("Log counters:");

        for (LogCounters counter
                : LogCounters.values()) {

            long value =
                    job.getCounters()
                            .findCounter(counter)
                            .getValue();

            System.out.printf(
                    "  %-15s %d%n",
                    counter.name(),
                    value
            );
        }
    }

    private static void printFailureDiagnostics(
            Job job
    ) throws Exception {

        JobStatus status =
                job.getStatus();

        System.err.printf(
                "Job ID: %s%n",
                job.getJobID()
        );

        System.err.printf(
                "Job state: %s%n",
                status.getState()
        );

        if (status.getFailureInfo() != null
                && !status.getFailureInfo()
                .trim()
                .isEmpty()) {

            System.err.printf(
                    "Failure info: %s%n",
                    status.getFailureInfo()
            );
        }

        TaskCompletionEvent[] events =
                job.getTaskCompletionEvents(0);

        for (TaskCompletionEvent event : events) {
            TaskCompletionEvent.Status eventStatus =
                    event.getStatus();

            if (eventStatus
                    != TaskCompletionEvent.Status.FAILED
                    && eventStatus
                    != TaskCompletionEvent.Status.KILLED
                    && eventStatus
                    != TaskCompletionEvent.Status.TIPFAILED) {

                continue;
            }

            System.err.printf(
                    "Task %s: %s%n",
                    event.getTaskAttemptId(),
                    eventStatus
            );

            String[] diagnostics =
                    job.getTaskDiagnostics(
                            event.getTaskAttemptId()
                    );

            for (String diagnostic : diagnostics) {
                System.err.println(diagnostic);
            }
        }
    }

    private static void configureWindowsHadoopNativeLibrary() {
        String osName =
                System.getProperty("os.name", "");

        if (!osName.startsWith("Windows")) {
            return;
        }

        String hadoopHome =
                System.getProperty("hadoop.home.dir");

        if (hadoopHome == null
                || hadoopHome.trim().isEmpty()) {
            hadoopHome =
                    System.getenv("HADOOP_HOME");
        }

        if (hadoopHome == null
                || hadoopHome.trim().isEmpty()) {
            hadoopHome =
                    "C:\\hadoop";
        }

        System.setProperty(
                "hadoop.home.dir",
                hadoopHome
        );

        java.nio.file.Path nativeLibrary =
                Paths.get(
                        hadoopHome,
                        "bin",
                        "hadoop.dll"
                );

        if (Files.isRegularFile(nativeLibrary)) {
            System.load(
                    nativeLibrary
                            .toAbsolutePath()
                            .toString()
            );
        } else {
            System.err.printf(
                    "WARN: hadoop.dll was not found at %s%n",
                    nativeLibrary.toAbsolutePath()
            );
        }
    }

    private static void loadHadoopConfiguration(
            Configuration configuration
    ) {

        String configDirectory =
                System.getenv("HADOOP_CONF_DIR");

        if (configDirectory == null
                || configDirectory.trim().isEmpty()) {
            return;
        }

        addIfPresent(
                configuration,
                configDirectory,
                "core-site.xml"
        );

        addIfPresent(
                configuration,
                configDirectory,
                "hdfs-site.xml"
        );

        addIfPresent(
                configuration,
                configDirectory,
                "mapred-site.xml"
        );
    }

    private static void addIfPresent(
            Configuration configuration,
            String directory,
            String fileName
    ) {

        java.nio.file.Path file =
                Paths.get(
                        directory,
                        fileName
                );

        if (Files.isRegularFile(file)) {
            configuration.addResource(
                    new Path(file.toUri())
            );
        }
    }

    private static void printUsage() {
        System.err.println("Usage:");

        System.err.println(
                "  java -jar ip-log-analysis-1.0.0.jar "
                        + "[hdfs-uri] "
                        + "<input> "
                        + "<output> "
                        + "[--overwrite]"
        );

        System.err.println("Examples:");

        System.err.println(
                "  java -jar ip-log-analysis-1.0.0.jar "
                        + "/thaipd/ip-log/input "
                        + "/thaipd/ip-log/output/run-01"
        );

        System.err.println(
                "  java -jar ip-log-analysis-1.0.0.jar "
                        + "webhdfs://171.226.11.41:30093 "
                        + "/thaipd/ip-log/input "
                        + "/thaipd/ip-log/output/run-01 "
                        + "--overwrite"
        );
    }

    private static final class Arguments {

        private final String hdfsUri;
        private final String inputPath;
        private final String outputPath;
        private final boolean overwrite;

        private Arguments(
                String hdfsUri,
                String inputPath,
                String outputPath,
                boolean overwrite
        ) {

            this.hdfsUri = hdfsUri;
            this.inputPath = inputPath;
            this.outputPath = outputPath;
            this.overwrite = overwrite;
        }

        private static Arguments parse(
                String[] args
        ) {

            boolean overwrite =
                    args.length > 0
                            && "--overwrite".equalsIgnoreCase(
                            args[args.length - 1]
                    );

            int valueCount =
                    overwrite
                            ? args.length - 1
                            : args.length;

            if (valueCount == 2) {
                return new Arguments(
                        DEFAULT_HDFS_URI,
                        args[0],
                        args[1],
                        overwrite
                );
            }

            if (valueCount == 3) {
                if (!isSupportedHdfsUri(args[0])) {
                    throw new IllegalArgumentException(
                            "HDFS URI must start with "
                                    + "hdfs://, webhdfs:// "
                                    + "or swebhdfs://: "
                                    + args[0]
                    );
                }

                return new Arguments(
                        args[0],
                        args[1],
                        args[2],
                        overwrite
                );
            }

            throw new IllegalArgumentException(
                    "Expected input and output paths, "
                            + "with an optional HDFS URI."
            );
        }

        private static boolean isSupportedHdfsUri(
                String value
        ) {

            return value != null
                    && (
                    value.startsWith("hdfs://")
                            || value.startsWith("webhdfs://")
                            || value.startsWith("swebhdfs://")
            );
        }
    }
}
