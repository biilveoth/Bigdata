# HDFS File Lister

Java CLI kết nối tới HDFS và liệt kê trực tiếp các file/thư mục con của một đường dẫn.

## Yêu cầu

- JDK 11 trở lên
- Maven 3.8 trở lên
- HDFS NameNode có thể truy cập từ máy chạy chương trình

## Build

```powershell
mvn clean package
```

Sau khi build, file chạy độc lập nằm tại `target/hdfs-file-lister-1.0.0.jar`.

## Chạy nhanh trên local (Windows)

Mở PowerShell tại thư mục project:

```powershell
.\run.ps1
```

List một thư mục cụ thể:

```powershell
.\run.ps1 -HdfsPath "/data"
```

Nếu PowerShell chặn script, chạy một lần cho process hiện tại:

```powershell
Set-ExecutionPolicy -Scope Process Bypass
.\run.ps1
```

## Chạy

Endpoint hiện được cấu hình mặc định là `http://171.226.11.41:30091`. Vì đây là
HTTP endpoint, chương trình tự chuyển nó sang scheme `webhdfs://` mà Hadoop client yêu cầu.

Chạy với endpoint mặc định và list thư mục gốc:

```powershell
java -jar target/hdfs-file-lister-1.0.0.jar
```

Hoặc truyền DFS URL và đường dẫn cần list:

```powershell
java -jar target/hdfs-file-lister-1.0.0.jar http://171.226.11.41:30091 /data
```

Hoặc cấu hình bằng biến môi trường:

```powershell
$env:DFS_URL = "http://171.226.11.41:30091"
$env:HADOOP_CONF_DIR = "C:\hadoop\etc\hadoop"
java -jar target/hdfs-file-lister-1.0.0.jar
```

Tham số thứ nhất mặc định là `http://171.226.11.41:30091`; tham số thứ hai mặc định là `/`.
Chương trình nhận `http://`, `https://`, `webhdfs://`, `swebhdfs://` và `hdfs://`.
Nếu `HADOOP_CONF_DIR` được khai báo, chương trình sẽ tự nạp `core-site.xml` và `hdfs-site.xml` trong thư mục đó.

Với cluster Kerberos, đăng nhập trước khi chạy:

```powershell
kinit user@YOUR.REALM
java -jar target/hdfs-file-lister-1.0.0.jar hdfs://namenode:8020 /data
```