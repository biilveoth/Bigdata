package com.example.hdfs;

import com.example.iplog.IpLogAnalysisDriver;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.BlockLocation;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.web.SWebHdfsFileSystem;
import org.apache.hadoop.hdfs.web.WebHdfsFileSystem;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Stream;

public final class HdfsFileLister {

    private static final String DEFAULT_HDFS_URI =
            "webhdfs://171.226.11.41:30093";

    private HdfsFileLister() {
    }

    public static void main(String[] args) throws Exception {
        // The analysis driver authenticates in run(), including direct ToolRunner calls.
        if (args.length > 0 && "analyze".equalsIgnoreCase(args[0])) {
            runAnalyze(args);
            return;
        }
        LdapAuthenticator.authenticateFromEnvironment();
        configureWindowsHadoopHome();

        if (args.length == 0) {
            list(DEFAULT_HDFS_URI, "/");
            return;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list":
                runList(args);
                break;

            case "upload":
                runUpload(args);
                break;

            default:
                // Hỗ trợ cách chạy cũ:
                // webhdfs://host:port /directory
                if (isSupportedHdfsUri(args[0])) {
                    String directory =
                            args.length > 1 ? args[1] : "/";

                    list(args[0], directory);
                } else {
                    printUsage();
                    System.exit(2);
                }
        }
    }

    private static void runList(String[] args) throws Exception {
        if (args.length > 3) {
            printUsage();
            System.exit(2);
        }

        String hdfsUri =
                args.length > 1
                        ? args[1]
                        : DEFAULT_HDFS_URI;

        String directory =
                args.length > 2
                        ? args[2]
                        : "/";

        list(hdfsUri, directory);
    }

    private static void runUpload(String[] args) throws Exception {
        boolean overwrite =
                args.length > 1
                        && "--overwrite".equalsIgnoreCase(
                        args[args.length - 1]
                );

        int argumentCount =
                overwrite ? args.length - 1 : args.length;

        if (argumentCount != 4) {
            printUsage();
            System.exit(2);
        }

        upload(
                args[1],
                Paths.get(args[2]),
                new Path(args[3]),
                overwrite
        );
    }

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

        /*
         * Dùng newInstance thay vì get để chắc chắn không lấy lại
         * WebHdfsFileSystem cũ từ cache.
         */
        try (FileSystem fileSystem =
                     FileSystem.newInstance(
                             URI.create(hdfsUri),
                             configuration
                     )) {

            printFileSystem(fileSystem);

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
                     FileSystem.newInstance(
                             URI.create(hdfsUri),
                             configuration
                     )) {

            printFileSystem(fileSystem);

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
            } else if (Files.isDirectory(localSource)) {
                uploadDirectory(
                        fileSystem,
                        localSource,
                        hdfsDestination,
                        overwrite,
                        uploadedCount,
                        skippedCount
                );
            } else {
                throw new IllegalArgumentException(
                        "Local path is not a regular file or directory: "
                                + localSource.toAbsolutePath()
                );
            }

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

    /**
     * Cấu hình filesystem client theo URI.
     *
     * Port 30093 là HttpFS gateway. Với Hadoop 2.7.7, HttpFS không
     * hỗ trợ GET_BLOCK_LOCATIONS. Vì vậy webhdfs được ánh xạ sang
     * class tương thích được khai báo phía dưới.
     */
    public static void configureFileSystemClient(
            Configuration configuration,
            String hdfsUri
    ) {

        URI uri = URI.create(hdfsUri);
        String scheme = uri.getScheme();

        if (scheme == null) {
            throw new IllegalArgumentException(
                    "HDFS URI does not contain a scheme: "
                            + hdfsUri
            );
        }

        configuration.set(
                "fs.defaultFS",
                hdfsUri
        );

        if ("webhdfs".equalsIgnoreCase(scheme)) {
            configuration.set(
                    "fs.webhdfs.impl",
                    HttpFsCompatibleWebHdfsFileSystem
                            .class
                            .getName()
            );

            configuration.setBoolean(
                    "fs.webhdfs.impl.disable.cache",
                    true
            );
        } else if ("swebhdfs".equalsIgnoreCase(scheme)) {
            configuration.set(
                    "fs.swebhdfs.impl",
                    HttpFsCompatibleSWebHdfsFileSystem
                            .class
                            .getName()
            );

            configuration.setBoolean(
                    "fs.swebhdfs.impl.disable.cache",
                    true
            );
        } else if (!"hdfs".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException(
                    "Unsupported filesystem scheme: "
                            + scheme
            );
        }
    }

    private static Configuration createConfiguration(
            String hdfsUri
    ) {

        if (!isSupportedHdfsUri(hdfsUri)) {
            throw new IllegalArgumentException(
                    "HDFS URI must start with hdfs://, "
                            + "webhdfs:// or swebhdfs://: "
                            + hdfsUri
            );
        }

        Configuration configuration =
                new Configuration();

        loadHadoopConfiguration(configuration);

        /*
         * Gọi sau loadHadoopConfiguration để cấu hình trong
         * core-site.xml không ghi đè HttpFS client.
         */
        configureFileSystemClient(
                configuration,
                hdfsUri
        );

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
                Paths.get(directory, fileName);

        if (Files.isRegularFile(file)) {
            configuration.addResource(
                    new Path(file.toUri())
            );
        }
    }

    private static void configureWindowsHadoopHome() {
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
            hadoopHome = "C:\\hadoop";
        }

        System.setProperty(
                "hadoop.home.dir",
                hadoopHome
        );
    }

    private static void printFileSystem(
            FileSystem fileSystem
    ) {

        System.out.printf(
                "Connected to %s%n",
                fileSystem.getUri()
        );

        System.out.printf(
                "FileSystem implementation: %s%n",
                fileSystem.getClass().getName()
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

    /**
     * WebHdfsFileSystem tương thích với HttpFS 2.7.7.
     *
     * HttpFS 2.7.7 không có operation GET_BLOCK_LOCATIONS.
     * LocalJobRunner không cần vị trí DataNode thật vì mapper chạy
     * trên máy local và đọc dữ liệu bằng HTTP.
     */
    public static final class HttpFsCompatibleWebHdfsFileSystem
            extends WebHdfsFileSystem {

        @Override
        public BlockLocation[] getFileBlockLocations(
                FileStatus file,
                long start,
                long length
        ) throws IOException {

            return syntheticBlockLocations(
                    file,
                    start,
                    length
            );
        }

        @Override
        public BlockLocation[] getFileBlockLocations(
                Path path,
                long start,
                long length
        ) throws IOException {

            return syntheticBlockLocations(
                    getFileStatus(path),
                    start,
                    length
            );
        }
    }

    /**
     * Phiên bản HTTPS tương ứng.
     */
    public static final class HttpFsCompatibleSWebHdfsFileSystem
            extends SWebHdfsFileSystem {

        @Override
        public BlockLocation[] getFileBlockLocations(
                FileStatus file,
                long start,
                long length
        ) throws IOException {

            return syntheticBlockLocations(
                    file,
                    start,
                    length
            );
        }

        @Override
        public BlockLocation[] getFileBlockLocations(
                Path path,
                long start,
                long length
        ) throws IOException {

            return syntheticBlockLocations(
                    getFileStatus(path),
                    start,
                    length
            );
        }
    }

    /**
     * Trả về block location logic thay vì gọi
     * GET_BLOCK_LOCATIONS tới HttpFS.
     */
    private static BlockLocation[] syntheticBlockLocations(
            FileStatus file,
            long start,
            long length
    ) {

        if (file == null) {
            return null;
        }

        if (start < 0 || length < 0) {
            throw new IllegalArgumentException(
                    "Block location start and length "
                            + "must be non-negative."
            );
        }

        if (file.isDirectory()
                || file.getLen() <= start
                || length == 0) {
            return new BlockLocation[0];
        }

        long effectiveLength =
                Math.min(
                        length,
                        file.getLen() - start
                );

        return new BlockLocation[]{
                new BlockLocation(
                        new String[]{"localhost"},
                        new String[]{"localhost"},
                        start,
                        effectiveLength
                )
        };
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
                "  list "
                        + "webhdfs://171.226.11.41:30093 "
                        + "/thaipd/ip-log/input"
        );

        System.err.println(
                "  upload "
                        + "webhdfs://171.226.11.41:30093 "
                        + "sample-data "
                        + "/thaipd/ip-log/input "
                        + "--overwrite"
        );

        System.err.println(
                "  analyze "
                        + "webhdfs://171.226.11.41:30093 "
                        + "/thaipd/ip-log/input "
                        + "/thaipd/ip-log/output/run-01 "
                        + "--overwrite"
        );

        System.err.println(
                "  analyze "
                        + "hdfs://hdfs-nodeport:8020 "
                        + "/thaipd/ip-log/input "
                        + "/thaipd/ip-log/output/run-01 "
                        + "--overwrite"
        );
    }
}
