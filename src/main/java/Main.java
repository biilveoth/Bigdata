import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    private Main() {
    }

    public static void main(String[] args) {
        String dfsUrl = args.length > 0
                ? args[0]
                : envOrDefault("DFS_URL", "hdfs://171.226.11.41:30091");
        String hdfsUri = toHadoopFileSystemUri(dfsUrl);
        String directory = args.length > 1 ? args[1] : "/";

        Configuration configuration = new Configuration();
        loadHadoopConfiguration(configuration);

        try (FileSystem fileSystem = FileSystem.get(URI.create(hdfsUri), configuration)) {
            Path path = new Path(directory);
            if (!fileSystem.exists(path)) {
                System.err.printf("HDFS path does not exist: %s%n", path);
                System.exit(2);
            }
            if (!fileSystem.isDirectory(path)) {
                System.err.printf("HDFS path is not a directory: %s%n", path);
                System.exit(2);
            }

            FileStatus[] entries = fileSystem.listStatus(path);
            System.out.printf("Connected to %s%n", fileSystem.getUri());
            System.out.printf("Listing %s (%d entries)%n", path, entries.length);
            for (FileStatus entry : entries) {
                System.out.printf(
                        "%-4s %s %12d %s %s%n",
                        entry.isDirectory() ? "DIR" : "FILE",
                        entry.getPermission(),
                        entry.getLen(),
                        Instant.ofEpochMilli(entry.getModificationTime()),
                        entry.getPath());
            }
        } catch (Exception exception) {
            System.err.printf("Cannot list HDFS path '%s' at '%s': %s%n",
                    directory, hdfsUri, exception.getMessage());
            exception.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void loadHadoopConfiguration(Configuration configuration) {
        String configDirectory = System.getenv("HADOOP_CONF_DIR");
        if (configDirectory == null || configDirectory.isBlank()) {
            return;
        }

        addIfPresent(configuration, configDirectory, "core-site.xml");
        addIfPresent(configuration, configDirectory, "hdfs-site.xml");
    }

    private static void addIfPresent(Configuration configuration, String directory, String fileName) {
        java.nio.file.Path file = Paths.get(directory, fileName);
        if (Files.isRegularFile(file)) {
            configuration.addResource(new Path(file.toUri()));
        }
    }

    private static String envOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String toHadoopFileSystemUri(String dfsUrl) {
        if (dfsUrl.startsWith("http://")) {
            return "webhdfs://" + dfsUrl.substring("http://".length());
        }
        if (dfsUrl.startsWith("https://")) {
            return "swebhdfs://" + dfsUrl.substring("https://".length());
        }
        return dfsUrl;
    }
}