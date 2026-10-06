#!/usr/bin/env python3
"""Deterministic fixtures, independent CSV oracle, and recorded Hadoop runs."""
import argparse
import collections
import csv
import datetime as dt
from decimal import Decimal, localcontext
import hashlib
import json
import os
from pathlib import Path
import random
import re
import statistics
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
HEADER = "order_id,customer_id,order_date,total_amount,category,status"
COUNTERS = (
    "INPUT_RECORDS", "HEADER_LINES", "EMPTY_LINES", "INVALID_RECORDS",
    "MAP_MATCHED", "MAP_REJECTED", "CUSTOMERS_ACCEPTED", "CUSTOMERS_REJECTED",
    "OUTPUT_RECORDS", "GROUP_REJECTED_RECORDS",
)
DEFAULTS = dict(min_amount="500000", max_amount="2000000", start_date="2026-09-01",
                end_date="2026-09-30", categories="Electronics,Books",
                statuses="COMPLETED", min_orders=3, min_total="3000000")


def write_json(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")


def fingerprint(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest() if hasattr(hashlib, "file_digest") else digest_stream(stream)


def digest_stream(stream):
    digest = hashlib.sha256()
    for block in iter(lambda: stream.read(1024 * 1024), b""):
        digest.update(block)
    return digest.hexdigest()


def generate(size, seed):
    rng = random.Random(seed)
    path = ROOT / "data" / ("orders-%d.csv" % size)
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.writer(stream, lineterminator="\n")
        writer.writerow(HEADER.split(","))
        for index in range(size):
            block, slot = divmod(index, 20)
            customer = "KH%06d" % (block * 3 + 1)
            date, amount, category, status = "2026-09-%02d" % rng.randint(1, 30), "1000000", "Books", "COMPLETED"
            order = "O%07d" % (index + 1)
            if slot == 0:
                date, amount = "2026-09-01", "500000"
            elif slot == 1:
                category = "Electronics"
            elif slot == 2:
                date, amount = "2026-09-30", "1500000"
            elif slot in (3, 4):
                customer = "KH%06d" % (block * 3 + 2)
                amount = "2000000" if slot == 3 else "1000000"
            elif slot in (5, 6, 7):
                customer, amount = "KH%06d" % (block * 3 + 3), "500000"
            elif slot == 8:
                date = "2026-08-31"
            elif slot == 9:
                date = "2026-10-01"
            elif slot == 10:
                amount = "499999.99"
            elif slot == 11:
                amount = "2000000.01"
            elif slot == 12:
                category = "Clothing"
            elif slot == 13:
                status = "CANCELLED"
            elif slot == 14:
                date = "2026-09-31"
            elif slot == 15:
                amount = "not-a-number"
            elif slot == 16:
                customer = ""
            elif slot == 17:
                order, amount = "QUOTED,%07d" % index, "500000.50"
            elif slot == 18:
                # Exact duplicate of slot zero, counted as another observation.
                order, date, amount = "O%07d" % (block * 20 + 1), "2026-09-01", "500000"
            else:
                writer.writerow([order, customer, date])
                continue
            writer.writerow([order, customer, date, amount, category, status])
    return dict(path=str(path.relative_to(ROOT)), records=size, bytes=path.stat().st_size,
                seed=seed, sha256=fingerprint(path), generator="20-row blocks, version 1")


def oracle(path, rules):
    """No imports/calls to Java: evaluate rows and groups with Python stdlib."""
    counts = dict.fromkeys(COUNTERS, 0)
    groups = collections.defaultdict(list)
    totals = collections.defaultdict(Decimal)
    categories = set(rules["categories"].split(","))
    statuses = set(rules["statuses"].split(","))
    low, high = Decimal(rules["min_amount"]), Decimal(rules["max_amount"])
    start, end = dt.date.fromisoformat(rules["start_date"]), dt.date.fromisoformat(rules["end_date"])
    with path.open(encoding="utf-8") as stream:
        for physical_line in stream:
            line = physical_line.rstrip("\r\n")
            if not line.strip():
                counts["EMPTY_LINES"] += 1
                continue
            if line.strip() == HEADER:
                counts["HEADER_LINES"] += 1
                continue
            counts["INPUT_RECORDS"] += 1
            try:
                row = next(csv.reader([line], strict=True))
                if len(row) != 6 or any(not field.strip() for field in row):
                    raise ValueError("schema")
                _, customer, date, money, category, status = [field.strip() for field in row]
                if not re.fullmatch(r"[0-9]+(?:\.[0-9]{1,2})?", money):
                    raise ValueError("money")
                if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", date):
                    raise ValueError("date format")
                date, amount = dt.date.fromisoformat(date), Decimal(money)
            except (ValueError, csv.Error):
                counts["INVALID_RECORDS"] += 1
                continue
            if low <= amount <= high and start <= date <= end and category in categories and status in statuses:
                counts["MAP_MATCHED"] += 1
                groups[customer].append(line)
                # Preserve cents even when uploaded money exceeds Decimal's default precision.
                with localcontext() as context:
                    context.prec = max(totals[customer].adjusted(), amount.adjusted(), 0) + 4
                    totals[customer] += amount
            else:
                counts["MAP_REJECTED"] += 1
    expected = collections.Counter()
    for customer, lines in groups.items():
        if len(lines) >= rules["min_orders"] and totals[customer] >= Decimal(rules["min_total"]):
            counts["CUSTOMERS_ACCEPTED"] += 1
            counts["OUTPUT_RECORDS"] += len(lines)
            expected.update(lines)
        else:
            counts["CUSTOMERS_REJECTED"] += 1
            counts["GROUP_REJECTED_RECORDS"] += len(lines)
    return counts, expected


def validate(output, source, rules):
    expected_counts, expected_rows = oracle(source, rules)
    actual_rows = collections.Counter()
    for part in sorted(output.glob("part-r-*")):
        actual_rows.update(part.read_text(encoding="utf-8").splitlines())
    metrics = json.loads((output / "_metrics.json").read_text(encoding="utf-8"))
    if metrics["counters"] != expected_counts:
        raise AssertionError("Counter mismatch: %r != %r" % (metrics["counters"], expected_counts))
    if actual_rows != expected_rows:
        raise AssertionError("Output multiset differs: missing=%d extra=%d" % (
            sum((expected_rows - actual_rows).values()), sum((actual_rows - expected_rows).values())))
    if not (output / "_SUCCESS").exists():
        raise AssertionError("Missing _SUCCESS")
    return metrics


def command_log(command, path):
    with path.open("w", encoding="utf-8") as log:
        log.write("COMMAND: " + " ".join(command) + "\n")
        log.flush()
        subprocess.run(command, cwd=ROOT, stdout=log, stderr=subprocess.STDOUT, check=True)


def run_suite(repeats):
    stamp = dt.datetime.now(dt.timezone.utc).strftime("%Y%m%dT%H%M%SZ")
    evidence = ROOT / "evidence" / stamp
    outputs = ROOT / "output" / stamp
    evidence.mkdir(parents=True)
    datasets = [generate(10000, 2026), generate(100000, 2026)]
    write_json(ROOT / "data" / "manifest.json", datasets)
    hadoop = Path(os.environ["HADOOP_HOME"]) / "bin" / "hadoop"
    environment = {
        "recorded_at_utc": dt.datetime.now(dt.timezone.utc).isoformat(),
        "JAVA_HOME": os.environ["JAVA_HOME"], "HADOOP_HOME": os.environ["HADOOP_HOME"],
        "HADOOP_CONF_DIR": os.environ["HADOOP_CONF_DIR"],
    }
    for label, command in [("java", ["java", "-version"]), ("maven", ["mvn", "-version"]),
                           ("hadoop", [str(hadoop), "version"]), ("os", ["uname", "-a"]),
                           ("python", ["python3", "--version"])]:
        result = subprocess.run(command, cwd=ROOT, capture_output=True, text=True, check=True)
        environment[label] = result.stdout + result.stderr
    write_json(evidence / "environment.json", environment)
    print("Building with clean target; log: %s" % (evidence / "build.log"), flush=True)
    command_log(["mvn", "--batch-mode", "clean", "package"], evidence / "build.log")
    jar = ROOT / "target" / "conditional-filter-1.0.0.jar"
    write_json(evidence / "artifacts.json", {"jar_sha256": fingerprint(jar), "datasets": datasets})
    scenarios = [("sample-default", ROOT / "data/orders.csv", {}, 1),
                 ("sample-min2", ROOT / "data/orders.csv", {"min_orders": 2}, 1),
                 ("edge-default", ROOT / "data/orders-edge.csv", {}, 1),
                 ("sample-no-match", ROOT / "data/orders.csv", {"min_total": "1000000000"}, 1),
                 ("10000-default", ROOT / "data/orders-10000.csv", {}, repeats),
                 ("100000-default", ROOT / "data/orders-100000.csv", {}, repeats)]
    rows = []
    for name, source, overrides, runs in scenarios:
        rules = dict(DEFAULTS, **overrides)
        for iteration in range(1, runs + 1):
            tag = "%s-run%d" % (name, iteration)
            output = outputs / tag
            command = [str(hadoop), "jar", str(jar), str(source), str(output)]
            for key, value in overrides.items():
                command.extend(["--" + key.replace("_", "-"), str(value)])
            started = time.perf_counter()
            command_log(command, evidence / (tag + ".log"))
            wall_seconds = time.perf_counter() - started
            metrics = validate(output, source, rules)
            row = dict(scenario=name, run=iteration, input_records=metrics["counters"]["INPUT_RECORDS"],
                       map_matched=metrics["counters"]["MAP_MATCHED"],
                       output_records=metrics["counters"]["OUTPUT_RECORDS"],
                       invalid_records=metrics["counters"]["INVALID_RECORDS"],
                       job_ms=metrics["job_elapsed_ms"], process_seconds=round(wall_seconds, 6),
                       verification="PASS", output=str(output.relative_to(ROOT)))
            rows.append(row)
            write_json(evidence / (tag + ".json"), dict(row, rules=rules, metrics=metrics,
                                                        input_sha256=fingerprint(source), command=command))
            print("%s: %d -> Map %d -> output %d; %.3fs; PASS" % (
                tag, row["input_records"], row["map_matched"], row["output_records"], wall_seconds), flush=True)
    with (evidence / "results.csv").open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=list(rows[0]))
        writer.writeheader()
        writer.writerows(rows)
    report = ["# Kết quả thử nghiệm thực tế", "", "Thời điểm UTC: " + stamp, "",
              "Mỗi lần chạy là một tiến trình `hadoop jar` mới. Không chạy các job song song.",
              "Đối chiếu độc lập bằng Python (CSV, Decimal, date): tất cả counters và toàn bộ",
              "đa tập dòng đầu ra, bao gồm số lần xuất hiện của bản ghi trùng, phải khớp.", "",
              "| Kịch bản | Lần | Đầu vào | Qua Map | Đầu ra | Lỗi | Job (ms) | Tiến trình (s) | Kiểm chứng |",
              "|---|---:|---:|---:|---:|---:|---:|---:|---|"]
    for row in rows:
        report.append("| {scenario} | {run} | {input_records} | {map_matched} | {output_records} | {invalid_records} | {job_ms} | {process_seconds:.3f} | {verification} |".format(**row))
    report.extend(["", "## Thời gian tổng hợp", "",
                   "Job đo quanh `waitForCompletion`; tiến trình đo từ lúc gọi `hadoop jar` đến khi thoát,",
                   "gồm khởi động JVM, cấu hình, job và ghi metrics; không gồm đối chiếu Python.", ""])
    for name, _, _, _ in scenarios:
        times = [row["process_seconds"] for row in rows if row["scenario"] == name]
        report.append("- %s: trung vị %.3f s; nhỏ nhất %.3f s; lớn nhất %.3f s; %d lần." % (
            name, statistics.median(times), min(times), max(times), len(times)))
    report.extend(["", "Đây là dữ liệu mô phỏng theo khối, không đại diện phân bố kinh doanh thực.",
                   "100.000 bản ghi là thử nghiệm tăng quy mô trong bài tập; không chứng minh hiệu năng",
                   "Big Data phân tán. Cache hệ điều hành và thứ tự chạy có thể ảnh hưởng thời gian.",
                   "", "Chi tiết môi trường, lệnh, log và JSON: `evidence/%s/`." % stamp,
                   "SHA-256 của dữ liệu và JAR lưu trong `artifacts.json` cùng thư mục."])
    (ROOT / "docs" / "KET_QUA_THU_NGHIEM.md").write_text("\n".join(report) + "\n", encoding="utf-8")
    write_json(ROOT / "evidence" / "latest.json", dict(directory=str(evidence.relative_to(ROOT)),
                                                      outputs=str(outputs.relative_to(ROOT)), runs=len(rows)))
    print("Evidence: " + str(evidence.relative_to(ROOT)), flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("action", choices=["generate", "run"])
    parser.add_argument("--repeats", type=int, default=3)
    args = parser.parse_args()
    if args.repeats < 1:
        parser.error("repeats must be positive")
    if args.action == "generate":
        datasets = [generate(10000, 2026), generate(100000, 2026)]
        write_json(ROOT / "data" / "manifest.json", datasets)
        print(json.dumps(datasets, indent=2))
    else:
        run_suite(args.repeats)


if __name__ == "__main__":
    main()
