# Kết luận rà soát trước demo

Kiểm tra ngày 02/10/2026 trên máy Mac hiện tại.

## Kết luận

Sẵn sàng demo kỹ thuật Bài 20 trong phạm vi Hadoop Standalone và dữ liệu đã kiểm tra.
Không phát hiện lỗi còn tồn tại ngăn cản các kịch bản demo bên dưới.
Chưa kết luận hồ sơ nộp đầy đủ: chưa có báo cáo PDF hoặc bảng phân công nhóm trong dự án.

## Phát hiện và sửa

1. Java từng chấp nhận năm 0000 hoặc năm mở rộng trong khi preview/oracle Python
   không hỗ trợ. Đã đồng bộ dữ liệu và cấu hình về năm 0001–9999, đúng YYYY-MM-DD;
   thêm kiểm thử Java cho các trường hợp bị loại và biên năm hợp lệ.
2. Decimal Python mặc định chỉ có 28 chữ số chính xác, có thể làm tròn tổng tiền
   rất lớn dù Java dùng BigDecimal chính xác. Đã điều chỉnh precision mỗi phép cộng;
   thêm kiểm thử ngưỡng chênh 0,01 VND với tiền vượt 28 chữ số.

## Minh chứng mới

- Java 11.0.32.1, Maven 3.9.16, Hadoop 3.3.6 trên macOS aarch64.
- Maven package: BUILD SUCCESS; 6 test Java đạt.
- Python unittest: 6 test đạt.
- Shell syntax và JavaScript syntax: đạt.
- `bash scripts/run-demo.sh`: exit 0; xuất O001/O002/O003 (thứ tự không đảm bảo).
- Bộ thực nghiệm 10 lần chạy: tất cả PASS; log/build/môi trường/hash tại
  `evidence/20261002T101958Z/`; kết quả tại `output/20261002T101958Z/`.
- 17 dòng: Map 8, output 3; min-orders 2: output 5.
- 19 dòng biên: Map 9, output 6; lỗi 8; giữ dòng trùng.
- Kịch bản không khớp: output 0, vẫn thành công.
- 10.000 dòng: Map 5.000, output 2.500, ba lần PASS.
- 100.000 dòng: Map 50.000, output 25.000, ba lần PASS.
- Playwright trên UI mới: sample 3, min-orders 2 cho 5, edge cho 6;
  cấu hình khoảng tiền đảo bị chặn; hủy job kết thúc cancelled.
- UI: chi tiết bản ghi, dropdown bàn phím, lịch sử, CSV/log/JSON trả HTTP 200.
- Các chiều rộng 320, 390, 768, 1024, 1440, 1920 px không tràn ngang toàn trang.
- Không có pageerror JavaScript trong các luồng trình duyệt đã kiểm tra.
- Không tìm thấy tên thuật toán/package của bài IP log/LDAP/Spark cũ trong
  mã ứng dụng, scripts, README và docs hiện tại. Git vẫn ghi nhận lịch sử xóa file cũ;
  đây không phải mã đang chạy.

## Giới hạn kết luận

Không kiểm tra mọi CSV tùy ý, môi trường máy khác hoặc cluster phân tán.
Giữ các hạn chế CSV một dòng, UTF-8 không BOM, trùng tính riêng, một Reducer
và file local như DAC_TA.md. Thời gian thử nghiệm không phải cam kết thời gian chạy.
Giao diện là công cụ demo local, không phải dịch vụ public nhiều người dùng.

Kịch bản trình bày và phần còn cần chuẩn bị: `docs/DEMO_CHECKLIST.md`.
