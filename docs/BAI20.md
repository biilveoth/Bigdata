# Thuật toán và hướng dẫn demo Bài 20

## Luồng xử lý

```text
CSV -> Map: lọc từng đơn -> Shuffle: nhóm theo customer_id
    -> Reduce: lọc theo số đơn và tổng tiền -> CSV kết quả
```

Map nhận `(offset, dòng CSV)` và xuất `(customer_id, dòng CSV)` nếu đạt.
Reduce nhận `(customer_id, danh sách dòng CSV)` và xuất `(NullWritable, dòng CSV)`.
NullWritable giúp kết quả không có khóa phụ hoặc dấu tab trước bản ghi.

## Giả mã

```text
MAP(offset, line):
    nếu line trống hoặc tiêu đề: tăng counter tương ứng; return
    tăng INPUT_RECORDS
    parse CSV và kiểm tra schema
    nếu lỗi: tăng INVALID_RECORDS; return
    nếu tiền, ngày, loại và trạng thái đều đạt:
        emit(customer_id, line); tăng MAP_MATCHED
    ngược lại: tăng MAP_REJECTED

REDUCE(customer_id, lines):
    count = 0; total = 0 (BigDecimal)
    tạo file tạm
    với mỗi line:
        count += 1; total += amount(line)
        ghi line vào file tạm
    nếu count >= min_orders AND total >= min_total:
        tăng CUSTOMERS_ACCEPTED
        đọc lại file tạm, xuất từng dòng; tăng OUTPUT_RECORDS
    ngược lại:
        tăng CUSTOMERS_REJECTED
        tăng GROUP_REJECTED_RECORDS thêm count
    xóa file tạm trong finally
```

Không dùng Combiner: cần giữ bản ghi gốc để xuất, phép lọc nhóm không thể thay
bằng cộng tiền đơn thuần. Thời gian xử lý Map/Reduce tuyến tính theo số bản ghi
được đọc, ngoài chi phí shuffle/sort của Hadoop. Bộ nhớ của ứng dụng Reducer
không tăng theo số dòng trong nhóm; file tạm tăng theo kích thước nhóm.

## Chạy trên Mac

```bash
bash scripts/install-hadoop.sh
bash -c 'source scripts/env.sh; mvn clean package'
bash scripts/run-demo.sh output/demo-01
```

Mặc định giữ O001, O002, O003 của KH01: 3 đơn, tổng đúng 3000000 VND.
KH02 có tổng 3000000 nhưng chỉ 2 đơn; KH03 có 3 đơn nhưng tổng chỉ 1500000.
O009-O014 không đạt bộ lọc Map; O015-O017 sai dữ liệu.

Thay điều kiện nhóm:

```bash
bash -c 'source scripts/env.sh; hadoop jar target/conditional-filter-1.0.0.jar \
  data/orders.csv output/demo-02 --min-orders 2'
cat output/demo-02/part-r-00000
```

Giữ thêm O004, O005 của KH02, tổng 5 bản ghi. Tên output phải mới.

Thay tất cả tham số:

```bash
bash -c 'source scripts/env.sh; hadoop jar target/conditional-filter-1.0.0.jar \
  data/orders.csv output/demo-03 \
  --min-amount 500000 --max-amount 2000000 \
  --start-date 2026-09-01 --end-date 2026-09-30 \
  --categories Electronics,Books --statuses COMPLETED \
  --min-orders 3 --min-total 3000000'
```

Input có thể là file hoặc thư mục các CSV cùng schema. Không dùng `data/`
làm input demo mặc định vì thư mục còn có các bộ dữ liệu khác và manifest.
Đặt các CSV cần xử lý chung vào thư mục riêng, không chứa JSON hoặc tài liệu.

## Giải thích khi phản biện

- Reduce cần thiết vì số đơn/tổng tiền của khách hàng không thể biết từ một dòng.
- Điều kiện nhóm tính trên các đơn đã qua Map; không tính đơn bị loại.
- Dữ liệu đầu ra vẫn là bản ghi đơn hàng, vì mục tiêu là lọc dữ liệu.
- Dùng BigDecimal để cộng tiền và kiểm tra ngưỡng chính xác.
- Dòng lỗi bị loại và đếm; cấu hình lỗi dừng chương trình.
- Dòng trùng được tính riêng theo chính sách đã ghi trong đặc tả.
- Standalone chạy trên một máy; shuffle/reduce vẫn do Hadoop thực hiện.

Xem [đặc tả đầy đủ](DAC_TA.md), [cài đặt](CAI_DAT_HADOOP.md)
và [kết quả thực nghiệm](KET_QUA_THU_NGHIEM.md).
