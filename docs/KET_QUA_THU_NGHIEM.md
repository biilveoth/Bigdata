# Kết quả thử nghiệm thực tế

Thời điểm UTC: 20261002T101958Z

Mỗi lần chạy là một tiến trình `hadoop jar` mới. Không chạy các job song song.
Đối chiếu độc lập bằng Python (CSV, Decimal, date): tất cả counters và toàn bộ
đa tập dòng đầu ra, bao gồm số lần xuất hiện của bản ghi trùng, phải khớp.

| Kịch bản | Lần | Đầu vào | Qua Map | Đầu ra | Lỗi | Job (ms) | Tiến trình (s) | Kiểm chứng |
|---|---:|---:|---:|---:|---:|---:|---:|---|
| sample-default | 1 | 17 | 8 | 3 | 3 | 1562 | 10.476 | PASS |
| sample-min2 | 1 | 17 | 8 | 5 | 3 | 1546 | 10.215 | PASS |
| edge-default | 1 | 19 | 9 | 6 | 8 | 1573 | 10.137 | PASS |
| sample-no-match | 1 | 17 | 8 | 0 | 3 | 1609 | 10.156 | PASS |
| 10000-default | 1 | 10000 | 5000 | 2500 | 2000 | 1574 | 10.553 | PASS |
| 10000-default | 2 | 10000 | 5000 | 2500 | 2000 | 1580 | 10.989 | PASS |
| 10000-default | 3 | 10000 | 5000 | 2500 | 2000 | 1565 | 10.567 | PASS |
| 100000-default | 1 | 100000 | 50000 | 25000 | 20000 | 4544 | 12.962 | PASS |
| 100000-default | 2 | 100000 | 50000 | 25000 | 20000 | 4539 | 13.138 | PASS |
| 100000-default | 3 | 100000 | 50000 | 25000 | 20000 | 4551 | 13.624 | PASS |

## Thời gian tổng hợp

Job đo quanh `waitForCompletion`; tiến trình đo từ lúc gọi `hadoop jar` đến khi thoát,
gồm khởi động JVM, cấu hình, job và ghi metrics; không gồm đối chiếu Python.

- sample-default: trung vị 10.476 s; nhỏ nhất 10.476 s; lớn nhất 10.476 s; 1 lần.
- sample-min2: trung vị 10.215 s; nhỏ nhất 10.215 s; lớn nhất 10.215 s; 1 lần.
- edge-default: trung vị 10.137 s; nhỏ nhất 10.137 s; lớn nhất 10.137 s; 1 lần.
- sample-no-match: trung vị 10.156 s; nhỏ nhất 10.156 s; lớn nhất 10.156 s; 1 lần.
- 10000-default: trung vị 10.567 s; nhỏ nhất 10.553 s; lớn nhất 10.989 s; 3 lần.
- 100000-default: trung vị 13.138 s; nhỏ nhất 12.962 s; lớn nhất 13.624 s; 3 lần.

Đây là dữ liệu mô phỏng theo khối, không đại diện phân bố kinh doanh thực.
100.000 bản ghi là thử nghiệm tăng quy mô trong bài tập; không chứng minh hiệu năng
Big Data phân tán. Cache hệ điều hành và thứ tự chạy có thể ảnh hưởng thời gian.

Chi tiết môi trường, lệnh, log và JSON: `evidence/20261002T101958Z/`.
SHA-256 của dữ liệu và JAR lưu trong `artifacts.json` cùng thư mục.
