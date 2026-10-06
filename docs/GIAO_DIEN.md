# Giao diện Bài 20

## Khởi động

Tại thư mục dự án:

```bash
bash scripts/start-ui.sh
```

Mặc định http://127.0.0.1:8765. Nếu cổng đã được dùng, server chọn cổng trống
tiếp theo và in đúng URL. Có thể yêu cầu cổng bằng `--port 8800`.
Nếu giao diện của dự án đã chạy, lệnh trả lại URL hiện có, không khởi động
server thứ hai hoặc thay đổi trạng thái lịch sử.
Script chọn Java 11 và Hadoop của dự án; build nếu chưa có JAR.
Sau khi sửa Java, build lại bằng `bash -c 'source scripts/env.sh; mvn package'`.
Hadoop cần được cài trước bằng `bash scripts/install-hadoop.sh`.

## Kịch bản demo bằng giao diện

1. Chọn Đơn hàng mẫu, xem 17 bản ghi và các dòng lỗi được đánh dấu.
2. Giữ điều kiện mặc định và nhấn Chạy bộ lọc. Kết quả: 3 đơn của KH01.
3. Mở Thống kê: 17 đầu vào, 8 qua Map, 3 đầu ra, 3 dòng lỗi.
4. Mở Log để thấy lệnh hadoop jar, Map/Reduce và trạng thái job thành công.
5. Đổi Số đơn tối thiểu thành 2 rồi chạy. Kết quả tăng lên 5 đơn.
6. Chọn Dữ liệu kiểm tra biên, khôi phục mặc định bằng nút mũi tên xoay.
   Kết quả 6 bản ghi, có hai dòng E001 giống nhau; giải thích chính sách trùng.
7. Chọn 10.000 hoặc 100.000 bản ghi và chạy; xem phân trang, tìm kiếm và tải CSV.
8. Mở lại một lần chạy trong Lịch sử; cấu hình và kết quả được khôi phục.

Các nhãn ngưỡng Map/Reduce giống đặc tả, không có thuật toán lọc thay thế trong
giao diện. Mỗi lần nhấn chạy gọi Hadoop 3.3.6 bằng subprocess với danh sách argv,
không nối điều kiện vào câu lệnh shell.

## Tải lên và xuất dữ liệu

Trên điện thoại, nút Điều kiện lọc mở hoặc thu biểu mẫu; khi bắt đầu chạy,
biểu mẫu thu lại và màn hình chuyển đến kết quả. Chọn một dòng trong bảng
(hoặc Enter khi dòng đang được focus) để xem đầy đủ giá trị và lỗi của bản ghi.
Nhãn Chưa chạy thay đổi phân biệt cấu hình đang chỉnh với cấu hình tạo kết quả.
Lịch sử có vùng cuộn riêng; nút Lịch sử ở thanh trên chuyển đến vùng này.

Nút upload cạnh Dữ liệu đầu vào nhận CSV UTF-8 không BOM, tối đa 16 MB.
Schema sáu cột theo docs/DAC_TA.md; mỗi dòng vật lý là một bản ghi.
Các dòng sai dữ liệu được nhận và đánh dấu để job có thể thống kê/loại chúng.
Tiêu đề hoặc file rỗng không được coi là dữ liệu hợp lệ để upload.

Tải CSV trả đúng file part-r-00000, không thêm tiêu đề hay sửa thứ tự, giữ dòng trùng.
Tải JSON gồm cấu hình, SHA-256 input/JAR, thời điểm, thời gian và toàn bộ counters.
Tải log trả log đầy đủ; khung log trên màn hình chỉ giữ 64 KB cuối để tránh quá tải.

## Lưu trữ và xác nhận kết quả

`output/ui/<job-id>/` chứa input.csv (snapshot), job.json, job.log và result/.
Snapshot đầu vào được giữ riêng nên lịch sử không bị thay đổi khi file gốc được sửa.
Lịch sử chọn Đầu vào cũng đọc snapshot của lần chạy đó.
File upload nằm trong output/ui/uploads/ và được nạp lại khi khởi động server.

Sau Hadoop exit code 0, server đối chiếu toàn bộ đa tập dòng đầu ra, tất cả counters
và _SUCCESS bằng bộ kiểm chứng Python độc lập. Chỉ khi khớp mới ghi trạng thái
Hoàn thành và mở tải kết quả. Không dùng số liệu mô phỏng hoặc progress giả.
Progress chỉ lấy từ log Hadoop; lúc chưa có log progress hiển thị Đang khởi động.
Thời gian job và thời gian toàn tiến trình được tách biệt trong Thống kê.

Một job chạy tại một thời điểm. Hủy job gửi SIGTERM tới nhóm tiến trình Hadoop;
job bị hủy không được hiển thị là kết quả hợp lệ, kể cả có file tạm.
Tắt server sẽ hủy các job đang chạy. Khi khởi động lại, lần chạy dang dở được
đánh dấu thất bại thay vì suy đoán rằng thành công. Các job đã hoàn thành vẫn tồn tại.

## Kiểm thử

```bash
python3 scripts/test_web_server.py
```

Kiểm tra schema/điều kiện, CSV lỗi, trùng, phân trang, tìm kiếm, upload, lịch sử upload
và API. Kiểm thử trình duyệt thực tế gồm chạy Hadoop mặc định/đổi ngưỡng,
upload, tải CSV, hủy job, màn hình desktop và mobile. Ảnh minh chứng lưu trong evidence/.

Server chỉ bind 127.0.0.1 để dùng trên máy demo. Đây là giao diện local của bài tập,
chưa triển khai tài khoản nhiều người dùng hoặc dịch vụ công khai.
Các biểu tượng dùng Lucide 0.468.0 (ISC), được lưu local để giao diện không cần CDN
khi chạy. Mã và giấy phép thư viện nằm trong ui/vendor/.
