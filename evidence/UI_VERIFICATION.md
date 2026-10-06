# Kiểm chứng giao diện Bài 20

Giao diện được kiểm tra bằng Playwright trên server local, dùng Hadoop 3.3.6
và JDK 11 của dự án. Các lần chạy là job thật, kết quả được server đối chiếu
độc lập bằng Python trước khi trả trạng thái thành công.

| Luồng | Kết quả đã kiểm chứng |
|---|---|
| Mặc định, 17 bản ghi | 8 qua Map, 3 đầu ra, đối chiếu PASS |
| min-orders=2 | 5 đầu ra; CSV tải xuống đúng 5 dòng |
| Tìm KH02 trong kết quả | 2 dòng |
| Input của lần chạy | Đọc snapshot, đúng 17 dòng |
| 100.000 bản ghi | 50.000 qua Map, 25.000 đầu ra, đối chiếu PASS |
| Phân trang kết quả lớn | Trang 2/1000, đúng 25 dòng |
| Upload CSV ba đơn | Nhận file, chạy Hadoop, đúng 3 đầu ra |
| Mở lịch sử sau reload | Khôi phục đúng dữ liệu, điều kiện và 3 kết quả upload |
| Hủy job | Trạng thái Đã hủy, không cho tải kết quả, có thể chạy job mới |
| Cấu hình khoảng tiền đảo | Hiển thị lỗi, thông báo còn sau nhịp polling |
| Tổng tiền nhóm quá cao | Job thành công, 0 đầu ra; CSV tải xuống rỗng |
| Thống kê | Biểu đồ có chiều rộng thực và đầy đủ counters |
| Desktop 1440px | Đã chụp và kiểm tra bố cục |
| Mobile 390px và 320px | Không tràn ngang toàn trang; bảng có vùng cuộn riêng |

Luồng min-orders=2 không phát sinh pageerror hoặc console error.
API có năm test unittest, tất cả đạt:

```bash
python3 scripts/test_web_server.py
```

Ảnh: ui-result-desktop.png, ui-result-mobile.png trong thư mục evidence/.
Lịch sử và log thật của các job giao diện: output/ui/<job-id>/.
Không thay đổi thuật toán Java hoặc bộ dữ liệu dùng trong bảng thử nghiệm trước đó.
