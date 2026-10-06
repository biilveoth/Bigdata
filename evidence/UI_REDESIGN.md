# Kiểm chứng giao diện sau chỉnh sửa

Đã rà soát bằng Playwright và ảnh chụp ở desktop/mobile:

- Bố cục hai cột, bảng có vùng cuộn và tiêu đề cố định, lịch sử cuộn riêng.
- Biểu mẫu mở/thu trên mobile và tự thu, chuyển đến kết quả khi chạy.
- Không tràn ngang toàn trang ở 320, 390, 768, 1024 và 1440 px.
- Mở chi tiết bản ghi bằng chuột và Enter; đóng bằng nút X hoặc Escape.
- Dòng lỗi có thể xem nguyên giá trị/lý do trong cửa sổ chi tiết.
- Cấu hình mặc định chạy Hadoop thật: 3 bản ghi; min-orders=2: 5 bản ghi.
- CSV tải về đúng 5 dòng với ngưỡng hai đơn.
- Nhãn Chưa chạy thay đổi xuất hiện sau khi sửa điều kiện.
- Hủy job từ giao diện mobile thành công.
- Biểu đồ thống kê có kích thước thực từ counters, không phải hình minh họa.
- Luồng chạy mặc định/đổi ngưỡng không có pageerror hoặc console error.
- Năm test backend/API đều đạt; JavaScript qua kiểm tra cú pháp.

Ảnh minh chứng: ui-redesign-desktop.png, ui-redesign-mobile.png,
ui-redesign-result.png, ui-redesign-mobile-result.png, ui-redesign-stats.png.
