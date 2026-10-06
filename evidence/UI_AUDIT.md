# Rà soát giao diện và luồng dữ liệu

Ngày kiểm tra: 02/10/2026.

## Phạm vi bài toán

Bài 20: lọc dữ liệu theo điều kiện bằng Hadoop MapReduce Standalone.
Map kiểm tra giá trị tiền, ngày, loại sản phẩm và trạng thái từng đơn.
Reduce nhóm theo mã khách hàng; số đơn và tổng tiền chỉ tính trên bản ghi qua Map.
Đầu ra giữ các đơn qua Map thuộc nhóm đạt cả hai ngưỡng Reduce.
Giao diện gọi chương trình Hadoop thực, không thay thuật toán bằng JavaScript.

## Thay đổi

- Bỏ giới hạn chiều rộng trang và lề ngoài của workspace.
- Giữ padding bên trong vùng làm việc để nội dung không sát mép.
- Phân biệt chưa chạy, đang xử lý, không có kết quả và kết quả bằng 0.
- Hiển thị trường rỗng bằng nhãn thiếu dữ liệu; giữ nguyên giá trị gốc trong chi tiết.
- Cho phép nội dung ô xuống dòng, không cắt mất giá trị dài.
- Tìm kiếm hiển thị số khớp trên tổng số bản ghi.
- Chặn phản hồi bảng/lịch sử cũ ghi đè lựa chọn mới.
- Xóa bảng cũ trong lúc tải và khóa phân trang cho tới khi có phản hồi.
- Làm rõ ngưỡng Reduce tính sau Map.

## Kiểm tra

- `python3 -m unittest discover -s scripts -p test_web_server.py`: 5 test đạt.
- `source scripts/env.sh && mvn test`: 5 test đạt; BUILD SUCCESS.
- `node --check ui/app.js`: đạt.
- Chạy trên UI: 17 đầu vào, 8 qua Map, 3 đầu ra.
- Chạy trên UI: 100.000 đầu vào, 50.000 qua Map, 25.000 đầu ra.
- Ngưỡng số đơn 2.147.483.647 trên bộ 100.000: đầu ra 0, đối chiếu đạt.
- Phân trang bộ 100.000: trang 2 / 4000.
- Tìm KH000001: 14 / 100.000 bản ghi.
- Kiểm tra chiều rộng 320, 390, 768, 1024, 1920 px: không tràn ngang toàn trang.
- Không ghi nhận lỗi JavaScript trong các luồng kiểm tra trên.

Ảnh kiểm tra: `ui-audit-desktop.png`, `ui-audit-result.png`, `ui-audit-mobile.png`.
Bảng trên mobile cuộn ngang riêng để giữ đủ sáu cột; bảng desktop cuộn dọc riêng.
Các phép kiểm tra trên là minh chứng cho phạm vi đã thử, không phải bảo đảm mọi tình huống.
