# Lộ trình tìm hiểu và checklist hoàn thành bài Big Data

## 1. Thông tin bài tập

### Chủ đề

Phân tích file log hệ thống để xác định địa chỉ IP truy cập nhiều nhất, áp dụng mô hình MapReduce và chạy thử nghiệm trên Hadoop Standalone hoặc framework được giảng viên chấp nhận.

### Mục tiêu đầu ra

Sau khi hoàn thành, nhóm cần có khả năng:

- Giải thích được các khái niệm Big Data và MapReduce ở mức cơ bản.
- Cài đặt và chạy được Hadoop trên môi trường thử nghiệm.
- Đọc file log và đếm số lượt xuất hiện của từng địa chỉ IP.
- Xác định được IP có số lượt truy cập lớn nhất.
- Chạy demo thành công với dữ liệu mẫu.
- Giải thích được mã nguồn và phần việc của từng thành viên.

### Giả định cần xác nhận

Tài liệu đề bài hiện chưa quy định rõ các điểm sau. Nhóm nên hỏi giảng viên trước khi chốt cách triển khai:

- Có bắt buộc dùng Hadoop Standalone hay được dùng Spark thay thế?
- Format chính xác của file log là gì và IP nằm ở trường nào?
- Một dòng log có được xem là một lượt truy cập không?
- Nếu nhiều IP có cùng số lượt truy cập lớn nhất thì phải xuất một IP hay tất cả IP?
- Có yêu cầu cụ thể về ngôn ngữ lập trình, phiên bản Java/Hadoop hoặc hệ điều hành không?

Trong lúc chuẩn bị, có thể tạm dùng quy ước: mỗi dòng log hợp lệ tương ứng với một lượt truy cập và trường đầu tiên là địa chỉ IP. Quy ước này phải được thay đổi nếu giảng viên cung cấp format khác.

## 2. Luồng xử lý cần hiểu

```text
File log
   ↓
Đọc và phân tích từng dòng
   ↓
Mapper: (IP, 1)
   ↓
Shuffle và Sort: nhóm các giá trị theo IP
   ↓
Reducer: cộng số lượt truy cập của từng IP
   ↓
Tìm giá trị lớn nhất
   ↓
Kết quả: IP và số lượt truy cập cao nhất
```

Có thể triển khai việc tìm giá trị lớn nhất bằng một bước xử lý thứ hai. Đây là cách dễ giải thích và dễ kiểm chứng trong báo cáo:

1. Job 1 đếm số lần xuất hiện của từng IP.
2. Job 2 đọc kết quả Job 1 và tìm IP có số đếm lớn nhất.

## 3. Các đầu mục cần tìm hiểu và việc cần làm

### 3.1. Phân tích yêu cầu và dữ liệu log

#### Cần tìm hiểu

- File log là gì?
- Một dòng log chứa những thông tin nào?
- Địa chỉ IP nằm ở vị trí nào?
- Dữ liệu có thể chứa dòng rỗng, dòng lỗi hoặc IP không hợp lệ không?
- “Truy cập nhiều nhất” được tính bằng số dòng log hay một tiêu chí khác?

#### Cần làm

- Tạo hoặc xin một file log mẫu.
- Chọn khoảng 10–20 dòng để đọc và phân tích thủ công.
- Ghi lại cấu trúc một dòng log.
- Viết quy tắc lấy IP từ một dòng.
- Quyết định cách xử lý dòng không hợp lệ.
- Tự tính kết quả bằng tay trên file mẫu.

#### Đầu ra cần có

- Mô tả format dữ liệu.
- Một file input mẫu.
- Bảng kết quả tính tay để đối chiếu với chương trình.
- Quy định rõ cách xử lý dữ liệu lỗi và trường hợp đồng hạng.

#### Đạt khi

Bạn có thể chỉ ra chính xác IP nằm ở đâu trong một dòng log và giải thích vì sao kết quả đếm thủ công là đúng.

### 3.2. Kiến thức nền về Big Data

#### Cần tìm hiểu

- Big Data là gì?
- Vì sao dữ liệu lớn cần xử lý phân tán?
- Batch Processing là gì?
- Cluster, node, job và task là gì?
- Hadoop giải quyết vấn đề nào?

#### Cần làm

- Viết phần lý thuyết ngắn khoảng 1–2 trang bằng lời của mình.
- Liên hệ bài toán log IP với xử lý dữ liệu lớn.
- So sánh ngắn giữa cách đếm bằng chương trình tuần tự và MapReduce.

#### Đầu ra cần có

- Phần lý thuyết Big Data trong báo cáo.
- Bảng hoặc đoạn so sánh xử lý tuần tự và xử lý phân tán.

#### Đạt khi

Bạn giải thích được tại sao bài toán đếm IP có thể được chia nhỏ cho nhiều Mapper và Reducer.

### 3.3. Mô hình MapReduce

#### Cần tìm hiểu

- Mapper nhận dữ liệu gì và xuất dữ liệu gì?
- Reducer nhận dữ liệu gì và xuất dữ liệu gì?
- Shuffle và Sort xảy ra ở đâu?
- Vì sao Mapper của bài này xuất `(IP, 1)`?
- Combiner có thể giúp ích như thế nào?
- Một Reducer có biết toàn bộ dữ liệu hay không?
- Vì sao việc tìm IP lớn nhất trên toàn bộ dữ liệu có thể cần bước xử lý bổ sung?

#### Cần làm

- Vẽ sơ đồ Mapper → Shuffle/Sort → Reducer.
- Viết giả mã cho Mapper.
- Viết giả mã cho Reducer.
- Tự mô phỏng quá trình xử lý với 5–10 dòng log.
- Ghi lại dữ liệu đầu vào và đầu ra sau từng giai đoạn.

#### Giả mã tham khảo

```text
Mapper(line):
    ip = lấy_IP(line)
    nếu line hợp lệ:
        emit(ip, 1)

Reducer(ip, values):
    total = tổng(values)
    emit(ip, total)
```

#### Đầu ra cần có

- Sơ đồ luồng MapReduce.
- Mô tả hàm Map.
- Mô tả hàm Reduce.
- Ví dụ minh họa trước và sau khi Shuffle/Sort.

#### Đạt khi

Bạn có thể giải thích được ý nghĩa của từng cặp key-value và mô tả được dữ liệu đi qua từng giai đoạn.

### 3.4. Cài đặt Hadoop Standalone

#### Cần tìm hiểu

- Java JDK và biến môi trường `JAVA_HOME`.
- Hadoop và biến môi trường liên quan.
- Chế độ Standalone của Hadoop.
- Cách kiểm tra Hadoop đã cài đặt thành công.
- Cách chạy một chương trình Hadoop bằng file `.jar`.
- Cách xem và xóa thư mục output.

#### Cần làm

- Xác định hệ điều hành và phiên bản công cụ sẽ sử dụng.
- Cài đặt Java và Hadoop theo hướng dẫn chính thức hoặc phụ lục của môn học.
- Chạy một ví dụ Hadoop đơn giản trước khi viết bài chính.
- Ghi lại từng lệnh cài đặt và chạy chương trình.
- Chụp lại kết quả kiểm tra môi trường và job chạy thành công.

#### Đầu ra cần có

- Tài liệu hướng dẫn cài đặt.
- Danh sách phiên bản công cụ.
- Các lệnh biên dịch và chạy.
- Ảnh chụp hoặc log chứng minh môi trường hoạt động.

#### Đạt khi

Bạn có thể cài lại môi trường hoặc hướng dẫn một thành viên khác chạy được ví dụ Hadoop.

### 3.5. Lập trình chương trình đếm IP

#### Cần tìm hiểu

- Cấu trúc một chương trình MapReduce.
- Cách khai báo Mapper và Reducer.
- Cách đọc từng dòng input.
- Cách tách chuỗi để lấy IP.
- Cách ghi output dạng key-value.
- Cách đóng gói chương trình thành file `.jar`.

#### Cần làm

- Viết Mapper và kiểm tra riêng logic lấy IP.
- Viết Reducer để cộng các giá trị.
- Chạy chương trình với file nhỏ.
- Đối chiếu kết quả với phép tính thủ công.
- Nếu cần, bổ sung Combiner để giảm lượng dữ liệu truyền giữa các bước.

#### Đầu ra cần có

- Mã nguồn Mapper.
- Mã nguồn Reducer.
- Mã nguồn khởi tạo và cấu hình job.
- File `.jar` hoặc hướng dẫn build tạo ra file `.jar`.
- Kết quả đếm số lần truy cập của từng IP.

#### Đạt khi

Với cùng một file input, kết quả chương trình trùng với kết quả tính tay và có thể chạy lại mà không cần sửa mã nguồn.

### 3.6. Tìm IP truy cập nhiều nhất

#### Cần tìm hiểu

- Cách tìm giá trị lớn nhất trong tập kết quả.
- Khác biệt giữa tìm lớn nhất trong từng Reducer và tìm lớn nhất trên toàn bộ dữ liệu.
- Cách xử lý nhiều IP có cùng số lượt truy cập.
- Cách nối hai job MapReduce nếu triển khai hai bước.

#### Cần làm

- Chọn và ghi rõ phương án triển khai.
- Nếu dùng hai job, mô tả input/output của từng job.
- Kiểm thử trường hợp có hai IP cùng đứng đầu.
- Kiểm thử trường hợp chỉ có một IP.
- Kiểm thử khi file không có dòng hợp lệ.

#### Đầu ra cần có

- Mô tả thuật toán tìm IP lớn nhất.
- Kết quả cuối cùng gồm IP và số lượt truy cập.
- Quy tắc xử lý đồng hạng.

#### Đạt khi

Chương trình luôn tìm đúng kết quả lớn nhất trên toàn bộ file, không chỉ đúng trong một phần dữ liệu.

### 3.7. Kiểm thử và đánh giá

#### Cần tìm hiểu

- Test case là gì?
- Expected output là gì?
- Vì sao cần kiểm thử dữ liệu biên và dữ liệu lỗi?
- Cách ghi nhận lỗi và kết quả kiểm thử.

#### Cần làm

Chuẩn bị ít nhất các trường hợp sau:

| Trường hợp | Mục đích |
|---|---|
| Một IP xuất hiện nhiều lần | Kiểm tra phép đếm |
| Nhiều IP khác nhau | Kiểm tra việc nhóm theo IP |
| Hai IP đồng hạng | Kiểm tra quy tắc kết quả |
| Dòng rỗng hoặc sai định dạng | Kiểm tra xử lý dữ liệu lỗi |
| File rỗng | Kiểm tra trường hợp không có dữ liệu |
| File lớn hơn file demo | Kiểm tra khả năng chạy thực tế |

#### Đầu ra cần có

- Bảng test case.
- Kết quả mong đợi.
- Kết quả thực tế.
- Kết luận đạt hoặc không đạt.

#### Đạt khi

Tất cả test case quan trọng đều có kết quả đúng hoặc có cách xử lý được mô tả rõ ràng.

### 3.8. Báo cáo, demo và phân công nhóm

#### Cần tìm hiểu

- Cách trình bày một bài toán xử lý dữ liệu.
- Cách mô tả thuật toán bằng sơ đồ và giả mã.
- Cách trình bày quy trình chạy demo.
- Cách giải thích phần việc cá nhân.

#### Cần làm

- Viết báo cáo theo trình tự: bài toán → dữ liệu → lý thuyết → thuật toán → cài đặt → kết quả.
- Chuẩn bị file input demo nhỏ, dễ kiểm tra bằng tay.
- Chuẩn bị sẵn các lệnh chạy.
- Chạy thử demo từ đầu đến cuối nhiều lần.
- Phân công công việc bằng văn bản.
- Mỗi thành viên chuẩn bị câu trả lời cho phần mình phụ trách.

#### Đầu ra cần có

- Báo cáo.
- Mã nguồn.
- Dữ liệu mẫu.
- Hướng dẫn chạy.
- Slide hoặc kịch bản demo.
- Bảng phân công thành viên.

#### Đạt khi

Nhóm có thể chạy demo trong thời gian cho phép và mỗi thành viên đều giải thích được phần việc của mình.

## 4. Checklist thực hiện theo thứ tự

### Giai đoạn 1: Làm rõ yêu cầu

- [ ] Xác nhận Hadoop hay Spark được phép sử dụng.
- [ ] Xác nhận format file log.
- [ ] Xác nhận cách tính lượt truy cập.
- [ ] Xác nhận cách xử lý IP đồng hạng.
- [ ] Xác nhận phiên bản công cụ và hệ điều hành nếu có yêu cầu.

### Giai đoạn 2: Học kiến thức nền

- [ ] Hiểu các khái niệm Big Data cơ bản.
- [ ] Hiểu Mapper.
- [ ] Hiểu Shuffle và Sort.
- [ ] Hiểu Reducer.
- [ ] Tự mô phỏng được bài toán với dữ liệu nhỏ.

### Giai đoạn 3: Chuẩn bị môi trường

- [ ] Cài đặt Java JDK.
- [ ] Cài đặt Hadoop Standalone hoặc framework được chấp nhận.
- [ ] Kiểm tra phiên bản và biến môi trường.
- [ ] Chạy được ví dụ Hadoop đơn giản.
- [ ] Ghi lại hướng dẫn cài đặt.

### Giai đoạn 4: Xây dựng chương trình

- [ ] Tạo file log mẫu.
- [ ] Viết logic lấy IP.
- [ ] Viết Mapper.
- [ ] Viết Reducer đếm IP.
- [ ] Viết bước tìm IP lớn nhất.
- [ ] Build chương trình thành công.
- [ ] Chạy job với file input mẫu.

### Giai đoạn 5: Kiểm thử

- [ ] Đối chiếu kết quả với tính toán thủ công.
- [ ] Kiểm thử IP lặp lại.
- [ ] Kiểm thử nhiều IP.
- [ ] Kiểm thử đồng hạng.
- [ ] Kiểm thử dòng lỗi và file rỗng.
- [ ] Lưu lại kết quả kiểm thử.

### Giai đoạn 6: Hoàn thiện hồ sơ và demo

- [ ] Hoàn thành báo cáo lý thuyết.
- [ ] Hoàn thành mô tả Map và Reduce.
- [ ] Hoàn thành hướng dẫn cài đặt và chạy.
- [ ] Đưa mã nguồn vào project.
- [ ] Đưa dữ liệu mẫu vào project.
- [ ] Ghi nhận phiên bản công cụ.
- [ ] Hoàn thành bảng phân công.
- [ ] Chạy thử demo từ đầu đến cuối.
- [ ] Chuẩn bị câu hỏi và câu trả lời bảo vệ.

## 5. Tiêu chí tự đánh giá trước khi nộp

Bài có thể xem là hoàn thành khi tất cả câu hỏi sau đều trả lời được “Có”:

- [ ] Chương trình có đọc dữ liệu từ file thay vì dùng dữ liệu viết sẵn trong code không?
- [ ] Chương trình có đếm đúng số lần xuất hiện của từng IP không?
- [ ] Kết quả IP nhiều nhất có đúng khi kiểm tra bằng tay không?
- [ ] Chương trình có xử lý hoặc mô tả rõ dữ liệu lỗi không?
- [ ] Có giải thích được Mapper, Shuffle/Sort và Reducer không?
- [ ] Có chạy được chương trình trên Hadoop hoặc framework đã thống nhất không?
- [ ] Có lệnh chạy cụ thể để người khác làm theo không?
- [ ] Có dữ liệu mẫu và output mẫu không?
- [ ] Có bằng chứng demo chạy thành công không?
- [ ] Có kiểm thử trường hợp đồng hạng không?
- [ ] Mỗi thành viên có phần việc và hiểu phần việc đó không?
- [ ] Báo cáo có mô tả cả lý thuyết lẫn phần triển khai không?

## 6. Cấu trúc báo cáo gợi ý

1. Giới thiệu bài toán.
2. Mô tả dữ liệu log.
3. Kiến thức Big Data liên quan.
4. Kiến thức và quy trình MapReduce.
5. Thiết kế thuật toán đếm IP.
6. Thiết kế thuật toán tìm IP lớn nhất.
7. Môi trường cài đặt.
8. Mã nguồn và cách chạy.
9. Dữ liệu mẫu và kết quả thực nghiệm.
10. Kiểm thử và đánh giá.
11. Phân công công việc.
12. Kết luận và hướng phát triển.
13. Tài liệu tham khảo.

## 7. Kết quả tối thiểu cần chứng minh trong buổi demo

```text
1. Hiển thị file log mẫu.
2. Giải thích vị trí của IP trong một dòng log.
3. Giải thích Mapper xuất (IP, 1).
4. Giải thích Reducer cộng các giá trị.
5. Chạy chương trình bằng Hadoop.
6. Hiển thị kết quả đếm.
7. Hiển thị IP truy cập nhiều nhất.
8. Đối chiếu kết quả với tính toán thủ công.
9. Trả lời câu hỏi về phần việc của từng thành viên.
```
