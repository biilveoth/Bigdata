# Bài 10: Phân tích log IP bằng Hadoop MapReduce

## 1. Tổng quan

Project đọc access log từ HDFS, đếm số lượt truy cập của từng địa chỉ IP và tìm
IP có số lượt truy cập lớn nhất.

Mô hình đang sử dụng:

- Source code Java được phát triển và chạy từ IntelliJ trên Windows.
- Mapper và Reducer chạy bằng Hadoop LocalJobRunner trên máy Windows.
- Dữ liệu input, intermediate output và final output được lưu trên HDFS.
- HDFS được triển khai trên Kubernetes.
- Máy local truy cập HDFS thông qua HttpFS NodePort 30093.

Pipeline gồm hai MapReduce Job:

    access.log
        |
        v
    Job 1: Đếm số lượt truy cập theo IP
        |
        v
    run-01_ip_counts/part-r-00000
        |
        v
    Job 2: Tìm IP có lượt truy cập lớn nhất
        |
        v
    run-01/part-r-00000

## 2. Mục tiêu nghiệp vụ

Đầu vào là một hoặc nhiều file access log theo định dạng thông dụng:

    192.168.1.10 - - [24/Aug/2026:10:15:20 +0700] "GET /index.html HTTP/1.1" 200 1024
    192.168.1.11 - - [24/Aug/2026:10:15:22 +0700] "GET /login HTTP/1.1" 200 512
    192.168.1.10 - - [24/Aug/2026:10:15:25 +0700] "GET /api/users HTTP/1.1" 200 2048

Quy tắc nghiệp vụ:

- Mỗi dòng log hợp lệ được tính là một lượt truy cập.
- Địa chỉ IP phải nằm ở token đầu tiên của dòng.
- Hỗ trợ IPv4 và IPv6 cơ bản.
- Dòng trống được thống kê nhưng không tham gia tính toán.
- Dòng sai định dạng hoặc IP không hợp lệ được thống kê và bỏ qua.
- Nếu nhiều IP có cùng số lượt lớn nhất, chương trình xuất tất cả IP đồng hạng.
- Các IP đồng hạng được sắp xếp để kết quả có thứ tự ổn định.

Ví dụ kết quả đếm:

    10.0.0.5       1
    192.168.1.10   5
    192.168.1.11   2
    2001:db8::1    3

Kết quả cuối:

    192.168.1.10   5

## 3. Kiến trúc triển khai

    IntelliJ / Java trên Windows
              |
              | LocalJobRunner
              | HTTP qua port 30093
              v
    Kubernetes NodePort
              |
              v
    HttpFS service:14000
              |
              | HDFS RPC nội bộ Kubernetes
              v
    NameNode:8020  <---->  DataNodes

Thông tin kết nối hiện tại:

| Thành phần | Địa chỉ |
|---|---|
| HDFS RPC NodePort | hdfs://171.226.11.41:30091 |
| NameNode Web NodePort | http://171.226.11.41:30092 |
| HttpFS NodePort | http://171.226.11.41:30093 |
| URI Java local nên dùng | webhdfs://171.226.11.41:30093 |

Khi dùng HttpFS:

- Máy Windows chỉ cần kết nối tới port 30093.
- Không cần truy cập trực tiếp Pod IP hoặc cổng truyền dữ liệu của từng DataNode.
- HttpFS đọc và ghi block HDFS thay cho client bên ngoài Kubernetes.
- Mapper và Reducer vẫn chạy trên Windows.

## 4. Chế độ chạy MapReduce

Driver cấu hình:

    configuration.set(
            "mapreduce.framework.name",
            "local"
    );

Điều này có nghĩa:

- Hadoop vẫn thực hiện Map, Combine, Shuffle, Sort và Reduce.
- Toàn bộ task chạy trong một JVM trên máy local.
- Computation chưa được phân phối lên các node Kubernetes.
- HDFS chỉ đóng vai trò lưu trữ dữ liệu.
- Mô hình phù hợp với bài tập, demo standalone và dữ liệu nhỏ.

Nếu chuyển sang YARN:

- Bỏ cấu hình local.
- Chạy Job bên trong cluster.
- Dùng URI RPC nội bộ, ví dụ hdfs://hdfs-nodeport:8020.
- Không dùng block location giả dành cho HttpFS local.

## 5. Luồng xử lý nghiệp vụ

### 5.1. Job 1 — Đếm số lượt truy cập theo IP

Input của Mapper:

    LongWritable: byte offset của dòng
    Text: nội dung dòng log

Mapper trích IP và phát:

    IP -> 1

Ví dụ:

    192.168.1.10 -> 1
    192.168.1.11 -> 1
    192.168.1.10 -> 1

Combiner cộng các giá trị cục bộ trước khi shuffle:

    192.168.1.10 -> 2
    192.168.1.11 -> 1

Shuffle/Sort gom tất cả record có cùng IP về cùng Reducer.

Reducer tính tổng cuối:

    192.168.1.10 -> 5
    192.168.1.11 -> 2

Output Job 1:

    /thaipd/ip-log/output/run-01_ip_counts/part-r-00000

### 5.2. Job 2 — Tìm IP truy cập nhiều nhất

Job 2 đọc output của Job 1:

    192.168.1.10   5
    192.168.1.11   2

Mapper Job 2 chuyển thành:

    NullWritable -> "5\t192.168.1.10"
    NullWritable -> "2\t192.168.1.11"

Tất cả record có cùng key NullWritable nên được gửi về một Reducer.

Reducer:

1. Duyệt tất cả cặp IP và count.
2. Theo dõi count lớn nhất.
3. Xóa danh sách cũ khi gặp count lớn hơn.
4. Thêm IP khi count bằng giá trị lớn nhất.
5. Sắp xếp danh sách IP đồng hạng.
6. Ghi kết quả cuối.

Output Job 2:

    /thaipd/ip-log/output/run-01/part-r-00000

## 6. Cấu trúc source code

    src/
    ├── main/
    │   ├── java/
    │   │   └── com/example/
    │   │       ├── hdfs/
    │   │       │   └── HdfsFileLister.java
    │   │       └── iplog/
    │   │           ├── IpLogAnalysisDriver.java
    │   │           ├── IpCountMapper.java
    │   │           ├── LongSumReducer.java
    │   │           ├── MaxIpMapper.java
    │   │           ├── MaxIpReducer.java
    │   │           ├── LogParser.java
    │   │           └── LogCounters.java
    │   └── resources/
    │       └── log4j.properties
    └── test/
        └── java/
            └── com/example/iplog/
                └── LogParserTest.java

## 7. Chi tiết các class

### 7.1. HdfsFileLister

Đây là class CLI tổng và là Main-Class của executable JAR.

Các command:

    list
    upload
    analyze

#### main()

Đọc command đầu tiên và điều hướng:

    list     -> runList()
    upload   -> runUpload()
    analyze  -> runAnalyze()

Nếu không truyền command nhưng tham số đầu là URI HDFS, chương trình chạy theo
cách tương thích cũ và thực hiện list.

#### runList()

- Đọc HDFS URI.
- Đọc thư mục cần list.
- Dùng URI mặc định nếu người dùng không truyền.
- Gọi list().

#### list()

- Tạo Hadoop Configuration.
- Chọn filesystem client theo URI.
- Kết nối tới HDFS hoặc HttpFS.
- Kiểm tra đường dẫn tồn tại.
- Kiểm tra đường dẫn là directory.
- Gọi listStatus().
- In loại file, permission, kích thước, thời gian và đường dẫn.

#### runUpload()

Đọc cú pháp:

    upload <hdfs-uri> <local-path> <hdfs-directory> [--overwrite]

Nếu có --overwrite, file HDFS cũ được ghi đè.

#### upload()

- Kiểm tra file hoặc folder local tồn tại.
- Kết nối HDFS.
- Tạo destination directory khi chưa tồn tại.
- Upload một file hoặc gọi uploadDirectory() với folder.
- Đếm số file uploaded và skipped.

#### uploadDirectory()

Sử dụng Files.walk() để duyệt đệ quy thư mục local.

Đường dẫn Windows dùng dấu gạch chéo ngược được chuyển sang dấu gạch chéo của
HDFS:

    relativePath.toString().replace('\\', '/')

#### copyFile()

- Skip nếu file tồn tại và không có --overwrite.
- Tạo parent directory.
- Gọi copyFromLocalFile().
- Giữ nguyên file local sau khi upload.

#### runAnalyze()

Bỏ command analyze khỏi mảng args rồi chuyển các tham số còn lại sang:

    IpLogAnalysisDriver.main(...)

#### configureFileSystemClient()

Đặt filesystem mặc định:

    configuration.set("fs.defaultFS", hdfsUri);

Với webhdfs, project dùng filesystem class tương thích HttpFS 2.7.7:

    fs.webhdfs.impl =
      HdfsFileLister$HttpFsCompatibleWebHdfsFileSystem

Filesystem cache bị tắt để không lấy lại client cũ:

    fs.webhdfs.impl.disable.cache = true

#### HttpFsCompatibleWebHdfsFileSystem

TextInputFormat gọi getFileBlockLocations() để chuẩn bị input split.

WebHdfsFileSystem mặc định biến lời gọi này thành:

    op=GET_BLOCK_LOCATIONS

HttpFS 2.7.7 không triển khai operation đó. Class tương thích override
getFileBlockLocations() và trả một block location logic là localhost.

Block location logic chỉ dùng để tạo input split. Dữ liệu thực tế vẫn được đọc
qua HttpFS bằng operation OPEN.

Giải pháp này phù hợp với LocalJobRunner vì mapper chạy trên chính máy local.
Nó không nên được dùng để thay thế block location thật trong YARN phân tán.

### 7.2. IpLogAnalysisDriver

Đây là class điều phối hai MapReduce Job.

Class kế thừa:

    Configured

để sử dụng Hadoop Configuration thông qua getConf().

Class implement:

    Tool

để chạy theo chuẩn ToolRunner.

#### main()

1. Thiết lập Hadoop native trên Windows.
2. Tạo Configuration.
3. Nạp core-site.xml, hdfs-site.xml và mapred-site.xml nếu tồn tại.
4. Gọi ToolRunner.
5. Trả exit code cho hệ điều hành.

#### run()

Luồng chính:

1. Parse command-line arguments.
2. Cấu hình HDFS/HttpFS.
3. Cấu hình LocalJobRunner.
4. Tạo input, intermediate và output Path.
5. Kiểm tra input/output.
6. Chạy Job 1.
7. In counter của Job 1.
8. Không chạy Job 2 nếu không có dòng hợp lệ.
9. Chạy Job 2.
10. In đường dẫn kết quả.

#### createCountJob()

Cấu hình Job 1:

| Thành phần | Class |
|---|---|
| Input format | TextInputFormat |
| Mapper | IpCountMapper |
| Combiner | LongSumReducer |
| Reducer | LongSumReducer |
| Map output | Text, LongWritable |
| Final output | Text, LongWritable |
| Output format | TextOutputFormat |

setJarByClass() chỉ cho Hadoop biết class đại diện của JAR. Khi chạy trực tiếp
từ IntelliJ, class nằm trong target/classes nên LocalJobRunner có thể cảnh báo
không tìm thấy Job JAR. Cảnh báo này không làm Job local thất bại.

#### createMaxJob()

Cấu hình Job 2:

| Thành phần | Class |
|---|---|
| Input format | TextInputFormat |
| Mapper | MaxIpMapper |
| Reducer | MaxIpReducer |
| Số Reducer | 1 |
| Map output | NullWritable, Text |
| Final output | Text, LongWritable |

Một Reducer bảo đảm tìm được maximum toàn cục.

#### validatePaths()

- Báo lỗi nếu input không tồn tại.
- Nếu có --overwrite, xóa intermediate và final output cũ.
- Nếu không có --overwrite, báo lỗi khi output đã tồn tại.

Hadoop MapReduce yêu cầu output directory chưa tồn tại trước khi Job bắt đầu.

#### printCounters()

In counter nghiệp vụ:

    TOTAL_LINES
    VALID_LINES
    EMPTY_LINES
    INVALID_LINES

#### printFailureDiagnostics()

Khi Job thất bại, method in:

- Job ID.
- Trạng thái Job.
- Failure information.
- Task attempt bị FAILED, KILLED hoặc TIPFAILED.
- Diagnostic message của từng task lỗi.

### 7.3. LogParser

Nhiệm vụ là lấy token đầu tiên của dòng log và kiểm tra đó có phải IP hợp lệ.

#### extractIp()

- Trả Optional.empty() nếu dòng null hoặc rỗng.
- Dùng split theo whitespace và chỉ lấy token đầu.
- Trả IP khi IPv4 hoặc IPv6 hợp lệ.
- Trả Optional.empty() khi sai.

#### isValidIpv4()

IPv4 phải:

- Có đúng bốn octet.
- Mỗi octet chỉ chứa chữ số.
- Mỗi octet dài tối đa ba ký tự.
- Giá trị nằm trong khoảng 0 đến 255.
- Không có số 0 thừa như 01.

#### isValidIpv6()

IPv6 phải:

- Chứa dấu hai chấm.
- Chỉ có ký tự hexadecimal và dấu hai chấm.
- Không có nhiều hơn một dấu rút gọn ::.
- Mỗi group dài tối đa bốn ký tự.
- Có đủ số group theo dạng nén hoặc không nén.

### 7.4. IpCountMapper

Khai báo:

    Mapper<LongWritable, Text, Text, LongWritable>

Luồng xử lý mỗi dòng:

1. Tăng TOTAL_LINES.
2. Nếu dòng rỗng, tăng EMPTY_LINES và return.
3. Gọi LogParser.extractIp().
4. Nếu IP không hợp lệ, tăng INVALID_LINES và return.
5. Tăng VALID_LINES.
6. Phát cặp IP và số 1.

Output:

    IP -> 1

### 7.5. LongSumReducer

Class này được dùng cho cả Combiner và Reducer của Job 1.

Với input:

    IP -> [1, 1, 1, ...]

class cộng tất cả giá trị rồi phát:

    IP -> tổng lượt

Phép cộng có tính kết hợp và giao hoán nên an toàn để dùng làm Combiner.

Combiner giúp giảm số record phải truyền qua Shuffle.

### 7.6. MaxIpMapper

Đọc từng dòng output của Job 1:

    IP count

Kiểm tra:

- Dòng không rỗng.
- Có đúng hai trường.
- Count chuyển được sang long.

Output:

    NullWritable -> "count\tIP"

NullWritable làm tất cả record có cùng key và đi về cùng Reducer.

### 7.7. MaxIpReducer

Reducer theo dõi:

    maximum
    danh sách IP có count bằng maximum

Khi count lớn hơn maximum:

- Cập nhật maximum.
- Xóa danh sách IP cũ.
- Thêm IP mới.

Khi count bằng maximum:

- Thêm IP vào danh sách đồng hạng.

Cuối cùng:

- Sắp xếp danh sách IP.
- Ghi mỗi IP cùng giá trị maximum.

### 7.8. LogCounters

Enum định nghĩa các counter nghiệp vụ:

| Counter | Ý nghĩa |
|---|---|
| TOTAL_LINES | Tổng số dòng Mapper nhận |
| VALID_LINES | Số dòng chứa IP hợp lệ |
| EMPTY_LINES | Số dòng rỗng |
| INVALID_LINES | Số dòng sai định dạng hoặc IP sai |

### 7.9. LogParserTest

Unit test kiểm tra:

- Trích xuất IPv4 từ common log format.
- Trích xuất IPv6 dạng nén.
- Từ chối dòng rỗng.
- Từ chối chuỗi không phải IP.
- Từ chối IPv4 lớn hơn 255.
- Từ chối IPv4 có số 0 thừa.

## 8. Kết quả chạy thực tế

Job 1:

    Input file size          = 950 bytes
    Input splits            = 1
    Map input records       = 12
    Map output records      = 11
    Combine output records  = 4
    Reduce input groups     = 4
    Reduce output records   = 4

Diễn giải:

- File có 12 dòng.
- Có 11 dòng hợp lệ.
- Có một dòng không hợp lệ.
- Có bốn IP khác nhau.
- Combiner đã gom 11 record thành bốn record trước Reduce.

Counter:

    TOTAL_LINES     12
    VALID_LINES     11
    EMPTY_LINES      0
    INVALID_LINES    1

Job 2:

    Map input records       = 4
    Map output records      = 4
    Reduce input groups     = 1
    Reduce output records   = 1

Diễn giải:

- Job 2 đọc bốn IP từ Job 1.
- Tất cả record được gom vào một group.
- Có một IP có lượt truy cập lớn nhất.
- Kết quả cuối có một dòng.

Thông báo thành công:

    Analysis completed.
    Result: /thaipd/ip-log/output/run-01/part-r-00000

## 9. Cấu hình Maven

Các phiên bản chính:

    Java target       8
    Hadoop            2.7.7
    JUnit             5.11.4

Hadoop client:

    org.apache.hadoop:hadoop-client:2.7.7

Không cần thêm hadoop-hdfs-httpfs vào dependency cho workaround hiện tại.
HttpFS 2.7.7 được cung cấp dưới dạng server WAR; project sử dụng
WebHdfsFileSystem có override block location.

Maven Shade Plugin đóng gói executable fat JAR với Main-Class:

    com.example.hdfs.HdfsFileLister

## 10. Cấu hình IntelliJ trên Windows

### Environment variables

Trong Run → Edit Configurations → Environment variables:

    HADOOP_USER_NAME=hadoop
    HADOOP_HOME=C:\hadoop
    HADOOP_CONF_DIR=C:\hadoop\etc\hadoop

Nếu nhập trên một dòng, dùng dấu chấm phẩy:

    HADOOP_USER_NAME=hadoop;HADOOP_HOME=C:\hadoop;HADOOP_CONF_DIR=C:\hadoop\etc\hadoop

Không dùng dấu phẩy để phân cách biến.

### VM options

    -Dhadoop.home.dir=C:\hadoop -Djava.library.path=C:\hadoop\bin

Các file native:

    C:\hadoop\bin\winutils.exe
    C:\hadoop\bin\hadoop.dll

## 11. Build project

Trong Maven tool window:

    Lifecycle → clean
    Lifecycle → test
    Lifecycle → package

Hoặc dùng terminal có Maven:

    mvn clean test
    mvn clean package

JAR sau khi build:

    target/ip-log-analysis-1.0.0.jar

## 12. Chạy và kiểm thử

### 12.1. Test toàn bộ MapReduce trực tiếp

Main class:

    com.example.iplog.IpLogAnalysisDriver

Program arguments:

    webhdfs://171.226.11.41:30093 /thaipd/ip-log/input /thaipd/ip-log/output/run-01 --overwrite

Không thêm command analyze khi chạy trực tiếp driver.

### 12.2. Chạy qua class CLI tổng

Main class:

    com.example.hdfs.HdfsFileLister

Program arguments:

    analyze webhdfs://171.226.11.41:30093 /thaipd/ip-log/input /thaipd/ip-log/output/run-01 --overwrite

### 12.3. List folder HDFS

    list webhdfs://171.226.11.41:30093 /thaipd/ip-log/input

### 12.4. Upload dữ liệu mẫu

    upload webhdfs://171.226.11.41:30093 sample-data /thaipd/ip-log/input --overwrite

### 12.5. Chạy unit test parser

Chạy class:

    com.example.iplog.LogParserTest

Hoặc Maven:

    mvn test

## 13. Kiểm tra output

Trong NameNode:

    hdfs dfs -cat /thaipd/ip-log/output/run-01_ip_counts/part-r-00000
    hdfs dfs -cat /thaipd/ip-log/output/run-01/part-r-00000

Từ máy local qua HttpFS:

    curl -L "http://171.226.11.41:30093/webhdfs/v1/thaipd/ip-log/output/run-01/part-r-00000?op=OPEN&user.name=hadoop"

Kiểm tra marker thành công:

    hdfs dfs -test -e /thaipd/ip-log/output/run-01/_SUCCESS

## 14. Xác thực và phân quyền

Cluster hiện tại:

    hadoop.security.authentication = simple
    hadoop.security.group.mapping =
      org.apache.hadoop.security.LdapGroupsMapping

Luồng identity:

    HADOOP_USER_NAME=hadoop
            |
            v
    HttpFS nhận username hadoop
            |
            v
    NameNode dùng LDAP để tra group của hadoop
            |
            v
    HDFS kiểm tra owner, group và permission

LDAP hiện chỉ dùng để tra group, không xác thực mật khẩu người dùng.

Java client không gửi LDAP bind DN hoặc LDAP password. File bind password trên
cluster là credential dịch vụ để NameNode tra cứu LDAP.

Với simple authentication, client có thể tự khai báo username. Vì vậy NodePort
30093 cần được giới hạn bằng firewall hoặc network policy. Để có xác thực mạnh,
nên dùng Kerberos/SPNEGO hoặc một gateway như Apache Knox có tích hợp LDAP.

Thư mục output phải cho user hadoop quyền ghi:

    hdfs dfs -chown -R hadoop:hadoop /thaipd/ip-log/output
    hdfs dfs -chmod -R 775 /thaipd/ip-log/output

## 15. Các cảnh báo có thể bỏ qua khi chạy local

### NativeCodeLoader

    Unable to load native-hadoop library for your platform

Nếu Job vẫn chạy thành công, Hadoop đang dùng Java implementation thay cho
native implementation.

### JobResourceUploader

    No job jar file set

Thường xuất hiện khi chạy trực tiếp từ IntelliJ. LocalJobRunner vẫn tìm thấy
class trong IntelliJ classpath.

### ProcfsBasedProcessTree

    ProcfsBasedProcessTree currently is supported only on Linux

Procfs không tồn tại trên Windows. Đây không phải lỗi nghiệp vụ.

### JVM Metrics already initialized

Hai Job chạy trong cùng một JVM nên Job 2 có thể thấy metrics đã được khởi tạo
bởi Job 1.

## 16. Giới hạn hiện tại

- Parser giả định IP luôn nằm ở token đầu tiên.
- Chưa xử lý X-Forwarded-For.
- Nếu log đứng sau proxy hoặc load balancer, IP đầu dòng có thể là IP proxy.
- Chưa phân tích theo thời gian, URL, status code hoặc user-agent.
- IPv6 parser chưa hỗ trợ zone ID và IPv4-mapped IPv6.
- Job 2 dùng một Reducer nên có thể thành bottleneck với số lượng IP rất lớn.
- MaxIpReducer giữ các IP đồng hạng trong bộ nhớ.
- Computation chạy local, chưa sử dụng khả năng phân tán của YARN.
- Simple authentication chưa xác minh danh tính người dùng.

## 17. Hướng phát triển

- Đọc X-Forwarded-For theo cấu hình.
- Dùng thư viện chuẩn để validate IPv6 đầy đủ.
- Phân tích top N IP thay vì chỉ maximum.
- Thống kê theo giờ, ngày hoặc khoảng thời gian.
- Phân tích URL được truy cập nhiều nhất.
- Thống kê HTTP status code.
- Phát hiện bot hoặc IP có tần suất bất thường.
- Chạy MapReduce trên YARN trong Kubernetes.
- Thêm integration test cho toàn bộ hai Job.
- Bật Kerberos/SPNEGO hoặc đặt gateway xác thực trước HttpFS.

## 18. Tiêu chí nghiệm thu

- Java kết nối được HDFS Kubernetes từ máy local.
- List và upload được dữ liệu qua HttpFS.
- Đọc được một hoặc nhiều file access log.
- Trích xuất đúng IPv4 và IPv6 cơ bản.
- Đếm đúng lượt truy cập theo IP.
- Xác định đúng IP có lượt truy cập lớn nhất.
- Xử lý được trường hợp nhiều IP đồng hạng.
- Không dừng Job 1 khi gặp dòng log lỗi.
- Thống kê chất lượng input bằng Hadoop Counter.
- Lưu intermediate và final output trên HDFS.
- Chạy được từ IntelliJ và executable JAR.
