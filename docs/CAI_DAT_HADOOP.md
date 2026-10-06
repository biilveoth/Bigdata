# Cài Hadoop Standalone và chuẩn bị minh chứng

## Bộ phiên bản

Hadoop 3.3.6, Java 11, Maven. Maven dependency và bản phân phối Hadoop dùng
cùng phiên bản. Bytecode ứng dụng biên dịch với release 8, chạy bằng JDK 11.
Theo [bảng Java của Apache Hadoop](https://cwiki.apache.org/confluence/spaces/HADOOP/pages/100827883/Hadoop+Java+Versions),
nhánh Hadoop 3.3 hỗ trợ Java 8 và Java 11 ở runtime.

## Cài trên Mac

Máy cần Homebrew, Maven, Python 3, curl, tar và shasum. Nếu chưa có Maven:

```bash
brew install maven
```

Tại thư mục gốc dự án:

```bash
bash scripts/install-hadoop.sh
```

Script cài Java 11 qua Homebrew, tải bản phân phối từ Apache Archive, so khớp
SHA-512 rồi giải nén vào `.tools/hadoop-3.3.6`. Log cài đặt nằm tại
`evidence/install-hadoop.log`. Không cần sudo, symlink JDK toàn hệ thống hoặc
thay đổi `.zshrc`. Đường dẫn Java riêng có thể đặt bằng BAI20_JAVA_HOME.

```bash
BAI20_JAVA_HOME=/duong/dan/jdk11 bash scripts/install-hadoop.sh
```

## Cấu hình Standalone

`config/hadoop/core-site.xml`: fs.defaultFS=file:///.
`config/hadoop/mapred-site.xml`: mapreduce.framework.name=local.
`config/hadoop/hadoop-env.sh`: heap tối đa 1024 MB cho lệnh Hadoop.
`scripts/env.sh` chọn JAVA_HOME, HADOOP_HOME, HADOOP_CONF_DIR và PATH trong
tiến trình script. Các lệnh ở terminal cần chạy trong bash như ví dụ dưới.

Theo [hướng dẫn Apache](https://hadoop.apache.org/docs/r3.3.6/hadoop-project-dist/hadoop-common/SingleCluster.html#Standalone_Operation),
Standalone chạy trong một tiến trình Java. Không cần format HDFS hoặc khởi động daemon.
Driver cũng đặt local và file:/// để chế độ demo không phụ thuộc cấu hình cluster.

## Kiểm tra và build

```bash
bash -c 'source scripts/env.sh; java -version; mvn -version; hadoop version'
bash -c 'source scripts/env.sh; mvn clean package'
bash scripts/run-demo.sh output/installation-demo
```

Lệnh Hadoop thực sự bên trong run-demo:

```bash
hadoop jar target/conditional-filter-1.0.0.jar data/orders.csv output/installation-demo
```

Output phải chưa tồn tại. Thành công được xác nhận bởi exit code 0, log job
completed successfully, `_SUCCESS`, counters và đối chiếu các dòng kết quả.

## Bộ minh chứng đã lưu

Chạy `bash scripts/run-experiments.sh` để ghi tự động:

- environment.json: Java, Maven, Hadoop, hệ điều hành, kiến trúc CPU và Python.
- build.log: lệnh mvn clean package, test và BUILD SUCCESS.
- artifacts.json: SHA-256 của JAR và các dữ liệu lớn.
- *.log: nguyên lệnh hadoop jar cùng stdout/stderr từng lần chạy.
- *.json/results.csv: cấu hình, counters, thời gian và kết quả đối chiếu PASS.
- part-r-00000 và _metrics.json trong thư mục output tương ứng.

Các đường dẫn của lần hoàn thành gần nhất nằm trong `evidence/latest.json`.
Không diễn giải cảnh báo thiếu thư viện native Linux trên macOS là job thất bại:
Hadoop có thể dùng lớp Java thay thế; kết quả phải được kiểm chứng như trên.
Bản phân phối được build trên Linux, còn chế độ Standalone của dự án được
chạy thử thực tế trên Mac; không khẳng định đây là triển khai cluster production.

## Sự cố thường gặp

- hadoop not found: chạy qua script hoặc source env.sh trong bash.
- Java không đúng phiên bản: kiểm tra `bash -c 'source scripts/env.sh; java -version'`.
- output already exists: dùng tên output mới; không tự xóa kết quả cũ.
- CSV không đạt: xem counters và đặc tả schema; tiền không có dấu phân cách nghìn.
- Tải bị ngắt: chạy lại trình cài; curl tiếp tục file tải dở và kiểm tra checksum.
- Thiếu JDK 11 ngoài Mac: đặt BAI20_JAVA_HOME trước khi chạy script.
