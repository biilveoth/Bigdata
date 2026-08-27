# Bài 10: Phân tích log IP bằng Hadoop MapReduce

## Mục tiêu

Tìm hiểu Hadoop MapReduce và xây dựng chương trình xác định địa chỉ IP truy cập
nhiều nhất từ file log hệ thống.

Mô hình triển khai:

- Chương trình Java MapReduce được phát triển và khởi chạy từ máy local.
- HDFS được triển khai trên Kubernetes.
- Dữ liệu đầu vào, dữ liệu trung gian và kết quả được lưu trên HDFS.

## Kiến trúc

```text
Java MapReduce trên local
          |
          | HDFS RPC
          v
171.226.11.41:30091
          |
          v
Kubernetes Service hdfs-nodeport
          |
          v
HDFS NameNode:8020 → HDFS DataNode
```

| Mục đích | Địa chỉ |
|---|---|
| HDFS RPC | `hdfs://171.226.11.41:30091` |
| WebHDFS | `http://171.226.11.41:30092` |

> Khi đọc hoặc ghi qua RPC, máy local phải kết nối được tới DataNode do NameNode
> trả về. Nếu DataNode chỉ công bố hostname nội bộ Kubernetes, cần expose các
> DataNode hoặc chạy chương trình MapReduce trong Kubernetes.

## Kế hoạch thực hiện

### Tuần 1: Tìm hiểu và chuẩn bị kiến trúc

#### Task 1.1: Tìm hiểu Hadoop MapReduce

- Tìm hiểu Mapper, Reducer và Combiner.
- Tìm hiểu luồng Input Split → Map → Shuffle/Sort → Reduce.
- Tìm hiểu `LongWritable`, `Text` và `IntWritable`.
- Tìm hiểu cách MapReduce đọc và ghi dữ liệu trên HDFS.
- Chạy thử chương trình WordCount.

#### Task 1.2: Xác định kiến trúc triển khai

- Xác định vị trí chạy chương trình MapReduce.
- Xác định namespace, NameNode và DataNode trên Kubernetes.
- Kiểm tra Service và NodePort của NameNode.
- Xác định HDFS RPC URL và WebHDFS URL.
- Xác định phương án kết nối DataNode từ máy local.

#### Task 1.3: Chuẩn bị project Java

- Tạo Maven project bằng IntelliJ IDEA.
- Cấu hình Java SDK và dependency Hadoop.
- Tạo `src/main/java` và `src/test/java`.
- Viết chương trình kiểm tra kết nối HDFS.
- List thử thư mục `/` trên HDFS.

#### Kết quả tuần 1

- Tài liệu tổng quan MapReduce và sơ đồ kiến trúc.
- Project Maven build được.
- Java kết nối và list được thư mục HDFS.

### Tuần 2: Chuẩn bị HDFS và dữ liệu log

#### Task 2.1: Kiểm tra HDFS trên Kubernetes

- Kiểm tra trạng thái NameNode và DataNode.
- Kiểm tra Service `hdfs-nodeport`.
- Kiểm tra HDFS RPC NodePort `30091`.
- Kiểm tra WebHDFS NodePort `30092`.
- Kiểm tra cluster bằng `hdfs dfsadmin -report`.

#### Task 2.2: Kiểm tra truy cập HDFS từ local

- List thư mục qua HDFS RPC.
- Tạo thư mục input trên HDFS.
- Upload và đọc lại file log.
- Kiểm tra kết nối tới DataNode.
- Xử lý lỗi DNS hoặc địa chỉ DataNode nội bộ Kubernetes.

#### Task 2.3: Chuẩn bị dữ liệu log

- Xác định định dạng log đầu vào.
- Tạo `access.log` có từ 100 đến 1.000 dòng.
- Thêm IP xuất hiện nhiều lần và IP xuất hiện một lần.
- Thêm dòng trống và dòng sai định dạng.
- Hỗ trợ IPv4; bổ sung IPv6 nếu đề bài yêu cầu.
- Upload log vào `/bigdata/ip-log/input` trên HDFS.

Ví dụ:

```text
192.168.1.10 - - [24/Aug/2026:10:15:20] "GET /index.html HTTP/1.1" 200 1024
192.168.1.11 - - [24/Aug/2026:10:15:22] "GET /login HTTP/1.1" 200 512
192.168.1.10 - - [24/Aug/2026:10:15:25] "GET /api/users HTTP/1.1" 200 2048
```

#### Task 2.4: Xây dựng module HDFS

- Nhận HDFS URI từ tham số hoặc biến môi trường.
- Kiểm tra input path tồn tại.
- List file, kích thước và thời gian cập nhật.
- Xử lý lỗi kết nối HDFS.

#### Kết quả tuần 2

- HDFS Kubernetes hoạt động ổn định.
- Máy local đọc được dữ liệu từ HDFS.
- File log mẫu đã được upload lên HDFS.

### Tuần 3: Xây dựng chương trình phân tích IP

#### Task 3.1: Job 1 — Đếm lượt truy cập theo IP

Mapper:

- Đọc từng dòng log, trích xuất và kiểm tra IP.
- Phát cặp `<IP, 1>`.
- Bỏ qua dòng sai định dạng.
- Dùng Hadoop Counter đếm dòng hợp lệ và không hợp lệ.

Combiner:

- Cộng số lượt cục bộ để giảm dữ liệu Shuffle.
- So sánh kết quả khi bật và tắt Combiner.

Reducer:

- Tính tổng lượt truy cập của từng IP.
- Ghi kết quả `<IP, tổng lượt>`.

Driver:

- Nhận input path và output path từ command line.
- Khai báo Mapper, Combiner, Reducer và các kiểu dữ liệu.
- Kiểm tra trạng thái hoàn thành của Job.

#### Task 3.2: Job 2 — Tìm IP truy cập nhiều nhất

- Đọc output của Job 1.
- Parse IP và tổng lượt truy cập.
- So sánh và tìm giá trị lớn nhất.
- Dùng một Reducer để nhận kết quả toàn cục.
- Xử lý trường hợp nhiều IP cùng đứng đầu.

#### Task 3.3: Kết nối hai Job

```text
Log trên HDFS
      ↓
Job 1: Đếm số lượt theo IP
      ↓
Kết quả trung gian trên HDFS
      ↓
Job 2: Tìm IP truy cập nhiều nhất
      ↓
Kết quả cuối trên HDFS
```

- Tạo thư mục output trung gian.
- Chỉ chạy Job 2 khi Job 1 thành công.
- Xử lý output đã tồn tại khi chạy lại.
- Trả exit code phù hợp nếu Job thất bại.

#### Task 3.4: Kiểm thử

- File bình thường và file rỗng.
- Dòng sai định dạng.
- Một IP và nhiều IP khác nhau.
- Nhiều IP cùng đứng đầu.
- Nhiều file trong input directory.
- Input không tồn tại hoặc output đã tồn tại.

#### Kết quả tuần 3

- Hai MapReduce Job hoàn chỉnh.
- Pipeline chạy được với dữ liệu trên HDFS Kubernetes.
- Có bộ dữ liệu và kết quả kiểm thử.

### Tuần 4: Hoàn thiện, đánh giá và demo

#### Task 4.1: Hoàn thiện chương trình

- Chuẩn hóa package và tên class.
- Tách Mapper, Reducer, Driver và tiện ích parse log.
- Bổ sung logging và xử lý exception.
- Đưa HDFS URI vào tham số hoặc file cấu hình.
- Đóng gói executable JAR.
- Hoàn thiện hướng dẫn build và chạy.

#### Task 4.2: Đánh giá

- Kiểm tra độ chính xác bằng cách đếm độc lập.
- Đo thời gian chạy và ghi nhận Hadoop counters.
- Đánh giá tác dụng của Combiner.
- Thử với 100, 1.000 và ít nhất 10.000 dòng log.

#### Task 4.3: Chuẩn bị demo

1. Hiển thị pod HDFS trên Kubernetes.
2. Hiển thị Service và NodePort.
3. Upload file log lên HDFS.
4. List file log từ Java local.
5. Chạy Job 1 và hiển thị số lượt của từng IP.
6. Chạy Job 2 và hiển thị IP truy cập nhiều nhất.
7. Kiểm tra output trực tiếp trên HDFS.

#### Task 4.4: Báo cáo và slide

- Trình bày bài toán, Hadoop, HDFS và MapReduce.
- Trình bày kiến trúc HDFS trên Kubernetes.
- Mô tả thuật toán, Mapper, Combiner, Reducer và hai Job.
- Trình bày hướng dẫn cài đặt và chạy.
- Tổng hợp kết quả thực nghiệm và các lỗi đã xử lý.
- Đánh giá ưu, nhược điểm và hướng phát triển.
- Chuẩn bị slide, kịch bản và video demo dự phòng.

#### Kết quả tuần 4

- Source code và executable JAR.
- File log demo và dữ liệu trên HDFS.
- README, báo cáo và slide thuyết trình.
- Kịch bản hoặc video demo dự phòng.

## Tiêu chí nghiệm thu

- Java kết nối được tới HDFS Kubernetes.
- Đọc được một hoặc nhiều file log trên HDFS.
- Trích xuất đúng địa chỉ IP.
- Đếm đúng số lượt truy cập của từng IP.
- Xác định đúng IP truy cập nhiều nhất.
- Không dừng khi gặp dòng log lỗi.
- Input, dữ liệu trung gian và output được lưu trên HDFS.
- Có hướng dẫn build, chạy và demo rõ ràng.

## Tổng hợp tiến độ

| Tuần | Kết quả chính |
|---|---|
| 1 | Hiểu MapReduce, thiết kế kiến trúc và kết nối HDFS |
| 2 | Chuẩn bị HDFS Kubernetes và dữ liệu log |
| 3 | Hoàn thành hai MapReduce Job và kiểm thử |
| 4 | Hoàn thiện sản phẩm, báo cáo, slide và demo |
