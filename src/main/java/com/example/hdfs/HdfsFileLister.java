package com.example.hdfs;

import com.example.iplog.IpLogAnalysisDriver;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Stream;

public final class HdfsFileLister {

    private static final String DEFAULT_HDFS_URI =
            "hdfs://171.226.11.3:30091";

    private HdfsFileLister() {
    }

    public static void main(String[] args) throws Exception {
        // Không có tham số: list thư mục gốc HDFS.
        if (args.length == 0) {
            list(DEFAULT_HDFS_URI, "/");
            return;
        }

        switch (args[0].toLowerCase()) {
            case "list":
                runList(args);
                break;

            case "upload":
                runUpload(args);
                break;

            case "analyze":
                runAnalyze(args);
                break;

            default:
                // Hỗ trợ cách chạy cũ:
                // hdfs://host:port /directory
                if (args[0].startsWith("hdfs://")) {
                    String directory = args.length > 1
                            ? args[1]
                            : "/";

                    list(args[0], directory);
                } else {
                    printUsage();
                    System.exit(2);
                }
        }
    }

    /**
     * List file hoặc folder trên HDFS.
     *
     * Cú pháp:
     * list [hdfs-uri] [hdfs-directory]
     */
    private static void runList(String[] args) throws Exception {
        if (args.length > 3) {
            printUsage();
            System.exit(2);
        }

        String hdfsUri = args.length > 1
                ? args[1]
                : DEFAULT_HDFS_URI;

        String directory = args.length > 2
                ? args[2]
                : "/";

        list(hdfsUri, directory);
    }

    /**
     * Upload file hoặc folder local lên HDFS.
     *
     * Cú pháp:
     * upload <hdfs-uri> <local-path> <hdfs-directory> [--overwrite]
     */
    private static void runUpload(String[] args) throws Exception {
        boolean overwrite =
                args.length > 1
                        && "--overwrite".equalsIgnoreCase(
                        args[args.length - 1]
                );

        int argumentCount = overwrite
                ? args.length - 1
                : args.length;

        if (argumentCount != 4) {
            printUsage();
            System.exit(2);
        }

        String hdfsUri = args[1];
        java.nio.file.Path localSource = Paths.get(args[2]);
        Path hdfsDestination = new Path(args[3]);

        upload(
                hdfsUri,
                localSource,
                hdfsDestination,
                overwrite
        );
    }

    /**
     * Chạy chương trình MapReduce phân tích log.
     *
     * Cú pháp:
     * analyze [hdfs-uri] <input> <output> [--overwrite]
     */
    private static void runAnalyze(String[] args) throws Exception {
        String[] analysisArguments =
                Arrays.copyOfRange(args, 1, args.length);

        IpLogAnalysisDriver.main(analysisArguments);
    }

    private static void list(
            String hdfsUri,
            String directory
    ) throws Exception {

        Configuration configuration =
                createConfiguration(hdfsUri);

        try (FileSystem fileSystem =
                     FileSystem.get(
                             URI.create(hdfsUri),
                             configuration
                     )) {

            Path path = new Path(directory);

            if (!fileSystem.exists(path)) {
                throw new IllegalArgumentException(
                        "HDFS path does not exist: " + path
                );
            }

            FileStatus status =
                    fileSystem.getFileStatus(path);

            if (!status.isDirectory()) {
                throw new IllegalArgumentException(
                        "HDFS path is not a directory: " + path
                );
            }

            FileStatus[] entries =
                    fileSystem.listStatus(path);

            System.out.printf(
                    "Connected to %s%n",
                    fileSystem.getUri()
            );

            System.out.printf(
                    "Listing %s (%d entries)%n",
                    path,
                    entries.length
            );

            for (FileStatus entry : entries) {
                System.out.printf(
                        "%-4s %s %12d %s %s%n",
                        entry.isDirectory() ? "DIR" : "FILE",
                        entry.getPermission(),
                        entry.getLen(),
                        Instant.ofEpochMilli(
                                entry.getModificationTime()
                        ),
                        entry.getPath()
                );
            }
        }
    }

    private static void upload(
            String hdfsUri,
            java.nio.file.Path localSource,
            Path hdfsDestination,
            boolean overwrite
    ) throws Exception {

        if (!Files.exists(localSource)) {
            throw new IllegalArgumentException(
                    "Local path does not exist: "
                            + localSource.toAbsolutePath()
            );
        }

        Configuration configuration =
                createConfiguration(hdfsUri);

        try (FileSystem fileSystem =
                     FileSystem.get(
                             URI.create(hdfsUri),
                             configuration
                     )) {

            if (!fileSystem.exists(hdfsDestination)) {
                boolean created =
                        fileSystem.mkdirs(hdfsDestination);

                if (!created) {
                    throw new IllegalStateException(
                            "Cannot create HDFS directory: "
                                    + hdfsDestination
                    );
                }
            }

            int[] uploadedCount = {0};
            int[] skippedCount = {0};

            if (Files.isRegularFile(localSource)) {
                Path destinationFile =
                        new Path(
                                hdfsDestination,
                                localSource
                                        .getFileName()
                                        .toString()
                        );

                copyFile(
                        fileSystem,
                        localSource,
                        destinationFile,
                        overwrite,
                        uploadedCount,
                        skippedCount
                );
            } else {
                uploadDirectory(
                        fileSystem,
                        localSource,
                        hdfsDestination,
                        overwrite,
                        uploadedCount,
                        skippedCount
                );
            }

            System.out.printf(
                    "Connected to %s%n",
                    fileSystem.getUri()
            );

            System.out.printf(
                    "Upload completed: %d uploaded, "
                            + "%d skipped, destination=%s%n",
                    uploadedCount[0],
                    skippedCount[0],
                    hdfsDestination
            );
        }
    }

    private static void uploadDirectory(
            FileSystem fileSystem,
            java.nio.file.Path localDirectory,
            Path hdfsDestination,
            boolean overwrite,
            int[] uploadedCount,
            int[] skippedCount
    ) throws Exception {

        try (Stream<java.nio.file.Path> paths =
                     Files.walk(localDirectory)) {

            Iterable<java.nio.file.Path> iterable =
                    paths::iterator;

            for (java.nio.file.Path localPath : iterable) {
                java.nio.file.Path relativePath =
                        localDirectory.relativize(localPath);

                String relativeText =
                        relativePath
                                .toString()
                                .replace('\\', '/');

                Path hdfsPath =
                        relativeText.isEmpty()
                                ? hdfsDestination
                                : new Path(
                                hdfsDestination,
                                relativeText
                        );

                if (Files.isDirectory(localPath)) {
                    if (!fileSystem.exists(hdfsPath)) {
                        fileSystem.mkdirs(hdfsPath);
                    }
                } else if (Files.isRegularFile(localPath)) {
                    copyFile(
                            fileSystem,
                            localPath,
                            hdfsPath,
                            overwrite,
                            uploadedCount,
                            skippedCount
                    );
                }
            }
        }
    }

    private static void copyFile(
            FileSystem fileSystem,
            java.nio.file.Path localFile,
            Path hdfsFile,
            boolean overwrite,
            int[] uploadedCount,
            int[] skippedCount
    ) throws Exception {

        if (fileSystem.exists(hdfsFile) && !overwrite) {
            System.out.printf(
                    "SKIP   %s (already exists)%n",
                    hdfsFile
            );

            skippedCount[0]++;
            return;
        }

        Path parent = hdfsFile.getParent();

        if (parent != null
                && !fileSystem.exists(parent)) {
            fileSystem.mkdirs(parent);
        }

        Path localHadoopPath =
                new Path(localFile.toUri());

        fileSystem.copyFromLocalFile(
                false,
                overwrite,
                localHadoopPath,
                hdfsFile
        );

        System.out.printf(
                "UPLOAD %s -> %s%n",
                localFile.toAbsolutePath(),
                hdfsFile
        );

        uploadedCount[0]++;
    }

    private static Configuration createConfiguration(
            String hdfsUri
    ) {
        if (!hdfsUri.startsWith("hdfs://")) {
            throw new IllegalArgumentException(
                    "HDFS URI must start with hdfs://: "
                            + hdfsUri
            );
        }

        Configuration configuration =
                new Configuration();

        configuration.set(
                "fs.defaultFS",
                hdfsUri
        );

        loadHadoopConfiguration(configuration);

        return configuration;
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
    }

    private static void addIfPresent(
            Configuration configuration,
            String directory,
            String fileName
    ) {
        java.nio.file.Path file =
                Paths.get(directory, fileName);

        if (Files.isRegularFile(file)) {
            configuration.addResource(
                    new Path(file.toUri())
            );
        }
    }

    private static void printUsage() {
        System.err.println("Usage:");

        System.err.println(
                "  list [hdfs-uri] [hdfs-directory]"
        );

        System.err.println(
                "  upload <hdfs-uri> "
                        + "<local-file-or-folder> "
                        + "<hdfs-directory> "
                        + "[--overwrite]"
        );

        System.err.println(
                "  analyze [hdfs-uri] "
                        + "<hdfs-input> "
                        + "<hdfs-output> "
                        + "[--overwrite]"
        );

        System.err.println();

        System.err.println("Examples:");

        System.err.println(
                "  list hdfs://171.226.11.3:30091 "
                        + "/thaipd/ip-log/input"
        );

        System.err.println(
                "  upload hdfs://171.226.11.3:30091 "
                        + "sample-data "
                        + "/thaipd/ip-log/input "
                        + "--overwrite"
        );

        System.err.println(
                "  analyze hdfs://171.226.11.3:30091 "
                        + "/thaipd/ip-log/input "
                        + "/thaipd/ip-log/output/run-01 "
                        + "--overwrite"
        );
    }
}