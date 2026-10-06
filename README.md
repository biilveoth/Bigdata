# Bài 20: Lọc dữ liệu theo điều kiện

Chương trình Java/Hadoop MapReduce lọc đơn hàng theo dải giá trị, thời gian,
loại sản phẩm và trạng thái, rồi lọc nhóm khách hàng theo số đơn và tổng tiền.
Chạy Hadoop Standalone trên máy cá nhân, dùng một job có Mapper và Reducer.

## Chạy nhanh trên Mac

Clone dự án cho thành viên nhóm:

```bash
git clone https://github.com/biilveoth/Bigdata.git
cd Bigdata
```

Máy mới cần cài môi trường và build bằng các lệnh ở mục Dòng lệnh bên dưới
trước khi mở giao diện. Hadoop, JAR và kết quả chạy local không được đưa vào Git.

### Giao diện web

```bash
bash scripts/start-ui.sh
```

Mở URL được in trong Terminal (mặc định http://127.0.0.1:8765).
Giao diện chọn/tải lên CSV, chỉnh điều kiện Map/Reduce, chạy Hadoop thật,
hủy job, xem kết quả phân trang, tìm kiếm, counters, log và lịch sử; tải CSV/JSON/log.
Mỗi job thành công được đối chiếu độc lập trước khi hiển thị hoàn thành.
Xem [hướng dẫn giao diện và demo](docs/GIAO_DIEN.md).

### Dòng lệnh

```bash
bash scripts/install-hadoop.sh
bash -c 'source scripts/env.sh; mvn clean package'
bash scripts/run-demo.sh
```

Trình cài tải Hadoop 3.3.6, kiểm tra SHA-512 và giải nén vào `.tools/`.
Java 11 được cài qua Homebrew khi cần. Script chọn Java cho dự án mà không sửa
Java mặc định hay cấu hình shell toàn máy. Cần Maven, Python 3 và Homebrew trên Mac.

## Tài liệu

- [Checklist chuẩn bị demo và phản biện](docs/DEMO_CHECKLIST.md).
- [Đặc tả bài toán và dữ liệu](docs/DAC_TA.md).
- [Thuật toán và hướng dẫn demo](docs/BAI20.md).
- [Cài đặt Hadoop Standalone và minh chứng](docs/CAI_DAT_HADOOP.md).
- [Thiết kế thử nghiệm và kiểm chứng độc lập](docs/THU_NGHIEM.md).
- [Kết quả đo thực tế](docs/KET_QUA_THU_NGHIEM.md).

## Dữ liệu và thử nghiệm

`data/orders.csv`: 17 bản ghi để đối chiếu bằng tay.
`data/orders-edge.csv`: lỗi định dạng, biên, số thập phân và bản ghi trùng.
`data/orders-10000.csv`, `data/orders-100000.csv`: dữ liệu mô phỏng tái lập.
`data/manifest.json`: seed, số bản ghi, dung lượng và SHA-256.

```bash
python3 scripts/experiments.py generate
bash scripts/run-experiments.sh --repeats 3
```

Lệnh thử nghiệm build sạch, chạy test, chạy `hadoop jar`, đối chiếu toàn bộ kết quả
và lưu log vào `evidence/<thời điểm UTC>/`. Kết quả CSV nằm trong
`output/<thời điểm UTC>/`; `evidence/latest.json` chỉ đến lần thử nghiệm gần nhất.

Không đưa `.tools/`, `target/`, `output/` vào Git. Log và bảng kết quả trong
`evidence/` được giữ để dùng làm minh chứng. Không tự xóa output khi chạy lại.
