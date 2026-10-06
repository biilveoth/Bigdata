# Thiết kế thử nghiệm và kiểm chứng

## Dữ liệu

| Bộ | Mục đích |
|---|---|
| orders.csv | 17 dòng dữ liệu, đối chiếu bằng tay |
| orders-edge.csv | Biên ngày/tiền, nhóm bằng/ngay dưới ngưỡng, dòng trùng, CSV có dấu phẩy, dòng lỗi |
| orders-10000.csv | 10.000 dòng dữ liệu mô phỏng |
| orders-100000.csv | 100.000 dòng dữ liệu mô phỏng |

Số bản ghi không bao gồm tiêu đề. Bộ edge có thêm dòng trống để kiểm chứng counter.
Bộ sinh dùng seed 2026 và khối 20 bản ghi. Mỗi khối tạo ba khách hàng mới:

- Khách hàng A: 3 đơn đạt, 1 đơn có mã chứa dấu phẩy và tiền thập phân,
  1 bản sao chính xác của đơn đầu; tổng 5 dòng qua Map, tổng tiền 4000000.50.
- Khách hàng B: 2 đơn đạt, tổng 3000000; mặc định bị Reduce loại vì thiếu số đơn.
- Khách hàng C: 3 đơn đạt, tổng 1500000; bị Reduce loại vì thiếu tổng tiền.
- 6 dòng hợp lệ nhưng bị Map loại: ngoài khoảng ngày/tiền, sai loại hoặc trạng thái.
- 4 dòng lỗi: ngày không tồn tại, tiền không phải số, khách hàng rỗng, thiếu cột.

Với cấu hình mặc định, mỗi khối có 10 dòng qua Map và 5 dòng đầu ra.
Do đó 10.000 dòng dự kiến có 5.000 dòng qua Map, 2.500 đầu ra, 2.000 dòng lỗi;
100.000 dòng dự kiến có 50.000 dòng qua Map, 25.000 đầu ra, 20.000 dòng lỗi.
Ngày của một số đơn được chọn bằng PRNG; seed cố định giúp tái lập file.
SHA-256 trong manifest xác định chính xác dữ liệu đã dùng.

## Kiểm chứng tính đúng

JUnit chạy job thật cho bộ nhỏ và edge, kiểm tra input thư mục nhiều file,
nhóm không đạt, không có đầu ra, cấu hình lỗi và output đã tồn tại.
Bộ edge chứng minh 1000000.10 + 999999.90 + 1000000 = 3000000 chính xác,
còn 1000000 + 1000000 + 999999.99 không đạt ngưỡng.

Ngoài JUnit, `scripts/experiments.py` thực hiện phép lọc độc lập bằng Python,
không gọi parser hoặc thuật toán Java. Đầu ra so theo Counter (đa tập dòng),
giữ thông tin số lần xuất hiện của dòng trùng. So sánh cả 10 counters,
kiểm tra `_SUCCESS`; chỉ ghi PASS khi tất cả khớp.

## Kịch bản chạy

1. Bộ 17 dòng với cấu hình mặc định: 3 dòng đầu ra.
2. Bộ 17 dòng, min-orders=2: 5 dòng đầu ra.
3. Bộ edge mặc định: 6 dòng đầu ra, gồm 2 lần xuất hiện của E001.
4. Bộ 17 dòng, min-total=1000000000: file đầu ra rỗng, job vẫn thành công.
5. Bộ 10.000 dòng: chạy 3 lần, mỗi lần JVM mới.
6. Bộ 100.000 dòng: chạy 3 lần, mỗi lần JVM mới.

## Đo thời gian và lưu minh chứng

```bash
bash scripts/run-experiments.sh --repeats 3
```

Script sinh dữ liệu, ghi môi trường, build sạch và chạy toàn bộ kịch bản nối tiếp.
Không dùng dữ liệu thời gian ước lượng. Có hai phép đo:

- job_elapsed_ms: đồng hồ đơn điệu quanh waitForCompletion, gồm submit và chờ job.
- process_seconds: đồng hồ đơn điệu của Python quanh hadoop jar, gồm khởi động
  JVM, đọc cấu hình, job và ghi metrics; không gồm kiểm chứng Python.

Ghi từng lần, trung vị, min và max. Không loại bỏ lần chậm hoặc chỉ báo cáo
lần nhanh nhất. Cache filesystem có thể ảnh hưởng; không có bước xóa cache.
Kết quả chỉ mô tả máy thử nghiệm hiện tại, không suy rộng tốc độ cho cluster.

`evidence/<UTC>/` lưu environment.json, build.log, artifacts.json, log/JSON từng
kịch bản và results.csv. `docs/KET_QUA_THU_NGHIEM.md` được sinh từ kết quả đo.
`evidence/latest.json` xác định lần hoàn thành gần nhất. Lần lỗi không được
đánh dấu hoàn thành; đọc log để biết nguyên nhân, sửa rồi chạy lại.
