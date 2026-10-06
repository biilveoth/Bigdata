# Chuẩn bị demo Bài 20

## Phạm vi cần giới thiệu

Tên đề tài: **Lọc dữ liệu theo điều kiện bằng Hadoop MapReduce**.
Đơn hàng là dữ liệu mô phỏng để minh họa thuật toán, không phải đề tài quản lý bán hàng.
Giao diện chỉ cấu hình, chạy và quan sát job Hadoop; không thay MapReduce bằng lọc JavaScript.

- Map: kiểm tra schema, lọc tiền/ngày/loại/trạng thái; xuất customer_id và dòng CSV.
- Shuffle: Hadoop gom các dòng qua Map theo customer_id.
- Reduce: tính số đơn và tổng tiền sau Map; giữ toàn bộ dòng của nhóm đạt hai ngưỡng.
- Kết quả: CSV đơn hàng được giữ lại, không chỉ một dòng tổng hợp mỗi khách hàng.
- Môi trường: Java 11, Hadoop 3.3.6 Standalone, một Reducer, file local.
- Không có HDFS/YARN daemon hoặc bằng chứng hiệu năng cluster phân tán.

## Trước khi vào lớp

1. Dùng chính máy đã kiểm tra; cắm nguồn, tắt chế độ ngủ trong lúc demo.
2. Không cần cài lại Hadoop hoặc chạy clean build giữa buổi demo.
3. Tại thư mục dự án, chạy `bash scripts/start-ui.sh`.
4. Mở URL Terminal in ra, thường là http://127.0.0.1:8765.
5. Kiểm tra trạng thái môi trường sẵn sàng, chọn Đơn hàng mẫu và khôi phục mặc định.
6. Chạy thử một lần trước buổi trình bày; xác nhận 17 đầu vào, 8 qua Map, 3 đầu ra.
7. Giữ Terminal server mở. Nếu đóng/tắt server, job đang chạy có thể bị hủy.
8. Chuẩn bị sẵn báo cáo và log để vẫn giải thích được bài nếu có sự cố trình chiếu.

Sau khi đã cài và build, giao diện dùng tài nguyên local, không cần CDN hoặc mạng
để chạy dữ liệu có sẵn. Lần cài/build đầu tiên có thể cần mạng tải phụ thuộc.

## Trình tự trình bày khoảng 5–8 phút

| Bước | Thao tác | Điểm cần giải thích |
|---|---|---|
| 1 | Mở dữ liệu mẫu | 6 cột, VND, ngày ISO; 17 dòng dữ liệu, 3 dòng lỗi |
| 2 | Chỉ cấu hình Map và Reduce | AND giữa các điều kiện; OR trong danh sách loại/trạng thái; biên được tính |
| 3 | Chạy mặc định | 8 dòng qua Map; chỉ KH01 đủ 3 đơn và tổng 3 triệu; đầu ra 3 |
| 4 | Mở Thống kê và Log | Counters, lệnh hadoop jar, Map/Reduce thành công, đối chiếu độc lập |
| 5 | Đổi số đơn tối thiểu thành 2 | KH02 được giữ thêm; đầu ra 5, thể hiện vai trò Reduce |
| 6 | Chọn dữ liệu biên và reset mặc định | Đầu ra 6; dòng E001 trùng được tính riêng; tiền thập phân chính xác |
| 7 | Chọn 100.000 và reset mặc định | 50.000 qua Map, 25.000 đầu ra, 20.000 lỗi; không gọi đây là benchmark cluster |
| 8 | Tải CSV hoặc mở lịch sử | Kết quả thực, snapshot input và cấu hình của từng lần chạy |

**Quan trọng:** đổi dataset không tự reset các điều kiện. Trước mỗi kịch bản mặc định,
nhấn nút khôi phục (mũi tên xoay) để tránh dùng ngưỡng của kịch bản trước.
Kết quả cũ vẫn thuộc cấu hình lần chạy cũ; nhãn chưa chạy thay đổi báo cấu hình đang chỉnh.
Thứ tự dòng trong nhóm không được đảm bảo, không dùng thứ tự để đánh giá tính đúng.

## Phương án dự phòng bằng Terminal

```bash
bash scripts/run-demo.sh
```

Script tự dùng tên output có thời điểm, chạy Hadoop và in ba dòng kết quả.
Nếu chỉ định output riêng, tên đó phải chưa tồn tại; không xóa kết quả cũ để chạy lại.
Cảnh báo native library trên Mac không đồng nghĩa job lỗi: kiểm tra exit code,
completed successfully, `_SUCCESS` và các dòng đầu ra.

## Câu hỏi nhóm phải trả lời được

- Vì sao cần Reduce? Vì số đơn/tổng tiền theo khách hàng cần nhiều dòng.
- Vì sao KH02/KH03 bị loại mặc định? KH02 thiếu số đơn, KH03 thiếu tổng tiền.
- Ngày và tiền ở đúng biên có được giữ không? Có.
- Dòng lỗi/trùng xử lý thế nào? Lỗi loại và đếm; trùng giữ và tính từng dòng riêng.
- Vì sao không dùng Combiner cộng tiền? Cần giữ dòng gốc để xuất khi nhóm đạt.
- Vì sao dùng file tạm trong Reduce? Giữ dòng chờ biết tổng nhóm mà không giữ cả nhóm trong RAM.
- Standalone có thật sự chạy MapReduce không? Có, Hadoop thực hiện job local; không chạy cluster.
- Thời gian trên giao diện là gì? Thời gian job; thời gian toàn tiến trình nằm trong thống kê.

## Demo sẵn sàng không đồng nghĩa hồ sơ nộp hoàn tất

- Hoàn thiện báo cáo PDF: lý thuyết/kiến trúc Hadoop, thuật toán, cài đặt, kết quả, hạn chế.
- Bổ sung số nhóm, thành viên, nhiệm vụ và đóng góp thực tế; không tự ghi đóng góp chưa xác nhận.
- Đặt tên PDF theo đề: `Báo cáo BTL-Nhóm [Số_nhóm].pdf`.
- Mỗi thành viên cần hiểu phần mình phụ trách và trả lời được câu hỏi trên.
- Slide không được yêu cầu bắt buộc trong nội dung đề đã cung cấp; chuẩn bị nếu lớp yêu cầu thêm.

Tài liệu đối chiếu: DAC_TA.md, BAI20.md, GIAO_DIEN.md, CAI_DAT_HADOOP.md,
KET_QUA_THU_NGHIEM.md. Minh chứng của lần thử nghiệm mới nhất: `evidence/latest.json`.
