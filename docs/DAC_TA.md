# Đặc tả Bài 20: Lọc dữ liệu theo điều kiện

## 1. Mục tiêu và phạm vi

Xây dựng chương trình Hadoop MapReduce lọc các bản ghi đơn hàng thỏa mãn
đồng thời điều kiện trên từng đơn và điều kiện tổng hợp theo khách hàng.
Mapper xử lý điều kiện trên bản ghi; Reducer xử lý điều kiện cần nhiều bản ghi.
Đầu ra là các đơn hàng được giữ lại, không phải chỉ bảng tổng hợp khách hàng.

Môi trường: Hadoop 3.3.6 Standalone, Java 11, một Reducer, file local.
Không khởi động NameNode, DataNode hoặc ResourceManager trong chế độ này.
Dữ liệu là mô phỏng phục vụ học tập và kiểm chứng, không chứa thông tin cá nhân thật.

## 2. Schema dữ liệu

CSV UTF-8, sáu cột theo đúng thứ tự:

| Cột | Kiểu | Ràng buộc | Ví dụ |
|---|---|---|---|
| order_id | Chuỗi | Không rỗng; mã quan sát, không dùng khử trùng | O001 |
| customer_id | Chuỗi | Không rỗng; khóa gom nhóm sau khi bỏ khoảng trắng hai đầu | KH01 |
| order_date | Ngày ISO | Ngày hợp lệ, dạng YYYY-MM-DD | 2026-09-15 |
| total_amount | Số thập phân | Không âm; tối đa 2 chữ số sau dấu chấm | 1000000.50 |
| category | Chuỗi | Không rỗng; phân biệt hoa/thường | Books |
| status | Chuỗi | Không rỗng; phân biệt hoa/thường | COMPLETED |

Tiêu đề chuẩn: `order_id,customer_id,order_date,total_amount,category,status`.
Tiêu đề có thể lặp ở mỗi file; được nhận diện theo nguyên dòng sau khi trim.
Không tự suy luận thứ tự cột hoặc sửa tiêu đề khác mẫu. Mỗi dòng vật lý chứa
một bản ghi; không hỗ trợ trường CSV có ký tự xuống dòng hoặc BOM đầu file.
Hỗ trợ dấu phẩy và dấu nháy trong trường khi được escape theo CSV.

Năm trong ngày dữ liệu và điều kiện lọc nằm trong 0001–9999, thống nhất giữa Java,
giao diện và bộ đối chiếu Python. Không chấp nhận năm 0000 hoặc năm mở rộng.

Tiền dùng VND cho toàn bộ dữ liệu. Dấu chấm là phần thập phân; không chấp nhận
dấu phân cách hàng nghìn, dạng khoa học, âm, NaN hoặc Infinity. Giá trị 0 hợp lệ
về dữ liệu, dù bị điều kiện mặc định loại. Tính tiền bằng BigDecimal, không dùng
float/double. Khoảng trắng hai đầu mỗi trường được bỏ khi kiểm tra; đầu ra giữ
nguyên dòng đầu vào. Ngày là ngày lịch, không phải timestamp; không có múi giờ.

## 3. Điều kiện ở Mapper

Giữ bản ghi hợp lệ nếu:

```text
500000 <= total_amount <= 2000000
AND 2026-09-01 <= order_date <= 2026-09-30
AND (category = Electronics OR category = Books)
AND status = COMPLETED
```

Cả hai đầu khoảng đều được tính. Danh sách categories/statuses được kết hợp OR
trong từng trường; các nhóm điều kiện được kết hợp AND. Phân biệt hoa/thường:
`books` không bằng `Books`. Không yêu cầu viết biểu thức tùy ý hoặc ngôn ngữ truy vấn.

Mapper xuất khóa customer_id và giá trị nguyên dòng CSV. Một đơn bị loại ở Map
không tham gia số đơn hoặc tổng tiền ở Reduce.

## 4. Điều kiện ở Reducer

Với tập đa bản ghi G(c) của khách hàng c sau Map:

```text
count(G(c)) >= 3
AND sum(total_amount của G(c)) >= 3000000 VND
```

Nếu đạt, xuất toàn bộ bản ghi trong G(c). Nếu không đạt, không xuất bản ghi nào
của nhóm. Cả ngưỡng số đơn và ngưỡng tiền là ngưỡng bao gồm giá trị bằng.
Khách hàng không có đơn qua Map không tạo nhóm Reduce và không được đếm trong
CUSTOMERS_REJECTED. Reduce không tính tổng trên toàn bộ lịch sử mua hàng.

## 5. Bản ghi trùng

Chính sách là giữ và tính từng dòng riêng (ngữ nghĩa tập đa bản ghi). Hai dòng
giống hoàn toàn vẫn tính hai lần trong số đơn, tổng tiền và số dòng đầu ra.
Các dòng trùng order_id nhưng khác thông tin cũng được xử lý độc lập; không
được coi là lỗi, không có quy tắc chọn phiên bản mới nhất. Khóa nhóm duy nhất
là customer_id, không phải order_id. Chính sách này được kiểm thử rõ ràng.

Vì dữ liệu có thể trùng, số đơn trong bài này thực chất là số lần ghi nhận
đạt bộ lọc. Khi áp dụng vào nghiệp vụ đòi hỏi mã đơn duy nhất, cần bổ sung bước
khử trùng và quy tắc giải quyết xung đột trước job hiện tại; đó là hướng mở rộng.

## 6. Dòng lỗi và cấu hình lỗi

- Dòng trống: tăng EMPTY_LINES, không tính INPUT_RECORDS.
- Tiêu đề chuẩn: tăng HEADER_LINES, không tính INPUT_RECORDS.
- Dòng còn lại: tăng INPUT_RECORDS.
- Thiếu/thừa cột, trường rỗng, CSV lỗi, ngày không tồn tại, tiền sai định dạng:
  tăng INVALID_RECORDS và bỏ dòng; job tiếp tục.
- Bản ghi hợp lệ nhưng không đạt điều kiện: tăng MAP_REJECTED.
- Không tự sửa dữ liệu, thay giá trị hoặc in nguyên dòng lỗi vào log.
- Cấu hình sai như khoảng đảo, min-orders < 1, danh sách rỗng, tiền sai định dạng,
  tham số không hỗ trợ/lặp: dừng trước job với lỗi; không âm thầm dùng mặc định.
- Thiếu input hoặc output đã tồn tại: dừng; không ghi đè hay xóa output.

## 7. Tham số

| Tham số | Mặc định | Ý nghĩa |
|---|---|---|
| --min-amount | 500000 | Tiền tối thiểu mỗi đơn |
| --max-amount | 2000000 | Tiền tối đa mỗi đơn |
| --start-date | 2026-09-01 | Ngày đầu khoảng |
| --end-date | 2026-09-30 | Ngày cuối khoảng |
| --categories | Electronics,Books | Một hoặc nhiều loại được chấp nhận |
| --statuses | COMPLETED | Một hoặc nhiều trạng thái được chấp nhận |
| --min-orders | 3 | Số dòng tối thiểu trong nhóm sau Map |
| --min-total | 3000000 | Tổng tiền tối thiểu trong nhóm sau Map |

## 8. Đầu ra và tiêu chí đúng

Thư mục output gồm `part-r-00000` (CSV sáu cột, không tiêu đề), `_SUCCESS`
và `_metrics.json` (phiên bản Hadoop, thời gian job, counters), cùng file CRC local.
Thứ tự bản ghi trong nhóm không được đảm bảo; so sánh bằng đa tập, không so thứ tự.
Không có đơn đạt điều kiện vẫn là job thành công, file kết quả rỗng.

Các đẳng thức cần đúng:

```text
INPUT_RECORDS = INVALID_RECORDS + MAP_MATCHED + MAP_REJECTED
MAP_MATCHED = OUTPUT_RECORDS + GROUP_REJECTED_RECORDS
Reduce input groups = CUSTOMERS_ACCEPTED + CUSTOMERS_REJECTED
```

Kết quả được đối chiếu với bộ đánh giá độc lập viết bằng Python dùng csv,
Decimal và date. So sánh tất cả counters và số lần xuất hiện của từng dòng,
không chỉ so số lượng kết quả.

## 9. Hạn chế

Một Reducer và môi trường standalone phục vụ demo, chưa đánh giá phân tán.
File tạm làm tăng I/O nhưng giảm bộ nhớ cần cho nhóm lớn. Không khử trùng,
không hỗ trợ CSV nhiều dòng, timestamp, biểu thức điều kiện tùy ý hoặc nhiều tiền tệ.
Các dữ liệu lớn hơn được sinh theo khối để có kết quả có thể giải thích;
không đại diện phân bố đơn hàng thực tế.
