#!/usr/bin/env python3
"""Local web workspace for real Hadoop jobs. No external Python dependencies."""
import argparse
import base64
import collections
import csv
import datetime as dt
import fcntl
from decimal import Decimal
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import mimetypes
import os
from pathlib import Path
import re
import shutil
import signal
import subprocess
import threading
import time
from urllib.parse import parse_qs, urlparse
import uuid

from experiments import DEFAULTS, HEADER, ROOT, fingerprint, validate, write_json

STORE = ROOT / "output/ui"
MAX_UPLOAD = 16 * 1024 * 1024
ACTIVE = {"queued", "running", "cancelling"}


def now():
    return dt.datetime.now(dt.timezone.utc).isoformat()


def parse_rules(raw):
    if not isinstance(raw, dict) or set(raw) != set(DEFAULTS):
        raise ValueError("Cần đủ tám điều kiện lọc hợp lệ.")
    rules = {}
    for key in ("min_amount", "max_amount", "min_total"):
        value = str(raw[key])
        if len(value) > 32 or not re.fullmatch(r"[0-9]+(?:\.[0-9]{1,2})?", value):
            raise ValueError("Tiền phải là số không âm, tối đa hai chữ số thập phân.")
        rules[key] = value
    if Decimal(rules["min_amount"]) > Decimal(rules["max_amount"]):
        raise ValueError("Giá trị tối thiểu không được lớn hơn tối đa.")
    for key in ("start_date", "end_date"):
        value = str(raw[key])
        if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", value):
            raise ValueError("Ngày phải có định dạng YYYY-MM-DD.")
        dt.date.fromisoformat(value)
        rules[key] = value
    if rules["start_date"] > rules["end_date"]:
        raise ValueError("Ngày bắt đầu phải trước hoặc bằng ngày kết thúc.")
    value = str(raw["min_orders"])
    if not re.fullmatch(r"[0-9]+", value) or not 1 <= int(value) <= 2147483647:
        raise ValueError("Số đơn tối thiểu phải là số nguyên dương.")
    rules["min_orders"] = int(value)
    for key in ("categories", "statuses"):
        if not isinstance(raw[key], str) or len(raw[key]) > 1024:
            raise ValueError("Danh sách loại hoặc trạng thái không hợp lệ.")
        values = [value.strip() for value in raw[key].split(",")]
        if not all(values):
            raise ValueError("Chọn ít nhất một loại và một trạng thái; không để phần tử rỗng.")
        rules[key] = ",".join(dict.fromkeys(values))
    return rules


def parsed_line(line):
    try:
        row = next(csv.reader([line], strict=True))
        if len(row) != 6:
            return row, "Cần đúng 6 cột"
        fields = [value.strip() for value in row]
        if not all(fields):
            return row, "Có trường rỗng"
        if not re.fullmatch(r"[0-9]+(?:\.[0-9]{1,2})?", fields[3]):
            return row, "Giá trị tiền không hợp lệ"
        if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", fields[2]):
            return row, "Định dạng ngày không hợp lệ"
        dt.date.fromisoformat(fields[2])
        return row, None
    except (ValueError, csv.Error):
        return [line], "Ngày hoặc CSV không hợp lệ"


def table_data(path, page=1, search="", limit=25):
    rows, matched, total = [], 0, 0
    start = (page - 1) * limit
    with path.open(encoding="utf-8") as stream:
        for number, raw in enumerate(stream, 1):
            line = raw.rstrip("\r\n")
            if not line.strip() or line.strip() == HEADER:
                continue
            total += 1
            if search and search.casefold() not in line.casefold():
                continue
            if start <= matched < start + limit:
                values, error = parsed_line(line)
                rows.append(dict(values=values, error=error, line=number))
            matched += 1
    return dict(rows=rows, total=total, matched=matched, page=page, limit=limit,
                pages=max(1, (matched + limit - 1) // limit))


class Workspace:
    def __init__(self, store=STORE):
        self.store = Path(store)
        self.store.mkdir(parents=True, exist_ok=True)
        (self.store / "uploads").mkdir(exist_ok=True)
        self.lock = threading.RLock()
        self.jobs = {}
        self.processes = {}
        self.threads = []
        self.datasets = {}
        for ident, name, filename in [
            ("sample", "Đơn hàng mẫu", "orders.csv"),
            ("edge", "Dữ liệu kiểm tra biên", "orders-edge.csv"),
            ("10k", "Đơn hàng · 10.000 bản ghi", "orders-10000.csv"),
            ("100k", "Đơn hàng · 100.000 bản ghi", "orders-100000.csv"),
        ]:
            path = ROOT / "data" / filename
            if path.exists():
                self.register_dataset(ident, name, path, False)
        for path in sorted((self.store / "uploads").glob("*.json")):
            entry = json.loads(path.read_text(encoding="utf-8"))
            source = self.store / "uploads" / (entry["id"] + ".csv")
            if source.exists():
                self.register_dataset(entry["id"], entry["name"], source, True)
        for path in self.store.glob("*/job.json"):
            job = json.loads(path.read_text(encoding="utf-8"))
            if job["status"] in ACTIVE:
                job.update(status="failed", error="Máy chủ đã dừng trước khi xác nhận kết quả.", finished_at=now())
                write_json(path, job)
            self.jobs[job["id"]] = job

    def register_dataset(self, ident, name, path, uploaded):
        records, invalid, categories, statuses = 0, 0, set(), set()
        with path.open(encoding="utf-8") as stream:
            for raw in stream:
                line = raw.rstrip("\r\n")
                if not line.strip() or line.strip() == HEADER:
                    continue
                records += 1
                row, error = parsed_line(line)
                if error:
                    invalid += 1
                else:
                    categories.add(row[4].strip())
                    statuses.add(row[5].strip())
        entry = dict(id=ident, name=name, filename=path.name, records=records, invalid=invalid,
                     bytes=path.stat().st_size, uploaded=uploaded, categories=sorted(categories),
                     statuses=sorted(statuses), sha256=fingerprint(path))
        with self.lock:
            self.datasets[ident] = (entry, path)
        return entry

    def catalog(self):
        with self.lock:
            return [value[0] for value in self.datasets.values()]

    def dataset(self, ident):
        with self.lock:
            if ident not in self.datasets:
                raise ValueError("Không tìm thấy dữ liệu.")
            return self.datasets[ident]

    def upload(self, data):
        if not isinstance(data, dict) or not isinstance(data.get("name"), str):
            raise ValueError("Tên file không hợp lệ.")
        name = Path(data["name"]).name[:200]
        if not name.lower().endswith(".csv"):
            raise ValueError("Chỉ nhận file CSV UTF-8.")
        try:
            content = base64.b64decode(data.get("content", ""), validate=True)
            text = content.decode("utf-8")
        except (ValueError, UnicodeDecodeError):
            raise ValueError("File phải có mã hóa UTF-8 hợp lệ.")
        if not content or len(content) > MAX_UPLOAD:
            raise ValueError("File phải có dữ liệu và không vượt quá 16 MB.")
        if "\x00" in text or text.startswith("\ufeff"):
            raise ValueError("CSV không hỗ trợ ký tự NUL hoặc BOM; lưu lại dạng UTF-8 không BOM.")
        ident = "upload-" + uuid.uuid4().hex
        path = self.store / "uploads" / (ident + ".csv")
        path.write_bytes(content)
        try:
            entry = self.register_dataset(ident, name, path, True)
            if not entry["records"]:
                raise ValueError("File không chứa bản ghi dữ liệu.")
            write_json(path.with_suffix(".json"), dict(id=ident, name=name))
            return entry
        except Exception:
            path.unlink(missing_ok=True)
            with self.lock:
                self.datasets.pop(ident, None)
            raise

    def save(self, job):
        write_json(self.store / job["id"] / "job.json", job)

    def list_jobs(self):
        with self.lock:
            return sorted((dict(job) for job in self.jobs.values()), key=lambda job: job["created_at"], reverse=True)

    def get_job(self, ident):
        with self.lock:
            if ident not in self.jobs:
                raise ValueError("Không tìm thấy lần chạy.")
            return dict(self.jobs[ident])

    def submit(self, data):
        if not isinstance(data, dict):
            raise ValueError("Yêu cầu chạy không hợp lệ.")
        rules = parse_rules(data.get("rules"))
        entry, source = self.dataset(data.get("dataset"))
        hadoop = Path(os.environ.get("HADOOP_HOME", "")) / "bin/hadoop"
        jar = ROOT / "target/conditional-filter-1.0.0.jar"
        if not hadoop.is_file() or not jar.is_file():
            raise ValueError("Chưa có Hadoop hoặc JAR. Cài môi trường và build trước khi chạy.")
        with self.lock:
            if any(job["status"] in ACTIVE for job in self.jobs.values()):
                raise ValueError("Đã có một job đang chạy. Chờ hoàn thành hoặc hủy job đó.")
            ident = uuid.uuid4().hex
            folder = self.store / ident
            folder.mkdir()
            snapshot = folder / "input.csv"
            shutil.copyfile(source, snapshot)
            command = [str(hadoop), "jar", str(jar), str(snapshot), str(folder / "result")]
            for key, value in rules.items():
                command.extend(["--" + key.replace("_", "-"), str(value)])
            job = dict(id=ident, name="Lần chạy " + dt.datetime.now().strftime("%H:%M:%S"),
                       dataset=entry, rules=rules, status="queued", created_at=now(),
                       command=command, input_sha256=fingerprint(snapshot), jar_sha256=fingerprint(jar))
            self.jobs[ident] = job
            self.save(job)
            worker = threading.Thread(target=self.execute, args=(ident,), daemon=True)
            self.threads.append(worker)
            worker.start()
            return dict(job)

    def execute(self, ident):
        folder = self.store / ident
        started = time.monotonic()
        try:
            with (folder / "job.log").open("w", encoding="utf-8") as log:
                with self.lock:
                    job = self.jobs[ident]
                    if job["status"] == "cancelling":
                        job.update(status="cancelled", finished_at=now())
                        self.save(job)
                        return
                    log.write("COMMAND: " + " ".join(job["command"]) + "\n")
                    log.flush()
                    process = subprocess.Popen(job["command"], cwd=ROOT, stdout=log,
                                               stderr=subprocess.STDOUT, start_new_session=True)
                    self.processes[ident] = process
                    job.update(status="running", started_at=now())
                    self.save(job)
                code = process.wait()
                elapsed = round(time.monotonic() - started, 3)
            with self.lock:
                cancelled = self.jobs[ident]["status"] == "cancelling"
            if cancelled:
                status, error, metrics = "cancelled", None, None
            elif code != 0:
                status, error, metrics = "failed", "Hadoop kết thúc với mã lỗi %d. Xem log để biết chi tiết." % code, None
            else:
                metrics = validate(folder / "result", folder / "input.csv", job["rules"])
                status, error = "succeeded", None
            with self.lock:
                job = self.jobs[ident]
                if job["status"] == "cancelling":
                    status, error, metrics = "cancelled", None, None
                job.update(status=status, error=error, finished_at=now(), process_seconds=elapsed,
                           metrics=metrics, verified=status == "succeeded")
                self.save(job)
        except Exception as exception:
            with self.lock:
                job = self.jobs[ident]
                job.update(status="cancelled" if job["status"] == "cancelling" else "failed",
                           error=str(exception), finished_at=now())
                self.save(job)
        finally:
            with self.lock:
                self.processes.pop(ident, None)

    def cancel(self, ident):
        with self.lock:
            job = self.jobs.get(ident)
            if not job or job["status"] not in ACTIVE:
                raise ValueError("Job không còn đang chạy.")
            job["status"] = "cancelling"
            self.save(job)
            process = self.processes.get(ident)
            if process and process.poll() is None:
                try:
                    os.killpg(process.pid, signal.SIGTERM)
                except ProcessLookupError:
                    pass
            return dict(job)

    def detail(self, ident):
        job = self.get_job(ident)
        log = self.store / ident / "job.log"
        text = ""
        if log.exists():
            with log.open("rb") as stream:
                stream.seek(max(0, log.stat().st_size - 65536))
                text = stream.read().decode("utf-8", errors="replace")
        progress = re.findall(r"map (\d+)% reduce (\d+)%", text)
        job["progress"] = list(map(int, progress[-1])) if progress else None
        job["log"] = text
        return job

    def stop(self):
        for job in self.list_jobs():
            if job["status"] in ACTIVE:
                self.cancel(job["id"])
        for worker in self.threads:
            worker.join(timeout=5)
        with self.lock:
            for process in self.processes.values():
                if process.poll() is None:
                    try:
                        os.killpg(process.pid, signal.SIGKILL)
                    except ProcessLookupError:
                        pass


class Handler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        if args and str(args[1]).startswith("4"):
            super().log_message(format, *args)

    def respond(self, content, content_type="application/json; charset=utf-8", status=200, attachment=None):
        body = content if isinstance(content, bytes) else json.dumps(content, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.send_header("Content-Security-Policy", "default-src 'self'; script-src 'self'; style-src 'self'; img-src 'self' data:; connect-src 'self'; object-src 'none'; frame-ancestors 'none'")
        if attachment:
            self.send_header("Content-Disposition", 'attachment; filename="%s"' % attachment)
        self.end_headers()
        try:
            self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def do_GET(self):
        try:
            self.get_route()
        except (ValueError, KeyError) as exception:
            self.respond(dict(error=str(exception)), status=400)
        except Exception as exception:
            self.respond(dict(error=str(exception)), status=500)

    def get_route(self):
        url = urlparse(self.path)
        path = url.path
        workspace = self.server.workspace
        if path == "/api/status":
            ready = (ROOT / "target/conditional-filter-1.0.0.jar").exists() and (Path(os.environ.get("HADOOP_HOME", "")) / "bin/hadoop").exists()
            self.respond(dict(ready=ready, engine="Hadoop 3.3.6", mode="Standalone", defaults=DEFAULTS))
        elif path == "/api/datasets":
            self.respond(workspace.catalog())
        elif path == "/api/jobs":
            self.respond(workspace.list_jobs())
        elif path.startswith("/api/datasets/"):
            parts = path.split("/")
            entry, source = workspace.dataset(parts[3])
            if len(parts) == 5 and parts[4] == "download":
                self.respond(source.read_bytes(), "text/csv; charset=utf-8", attachment="dataset.csv")
            else:
                self.respond(table_data(source, **self.pagination(url)))
        elif path.startswith("/api/jobs/"):
            parts = path.split("/")
            ident = parts[3]
            job = workspace.get_job(ident)
            folder = workspace.store / ident
            if len(parts) == 4:
                self.respond(workspace.detail(ident))
            elif parts[4] == "rows":
                if job["status"] != "succeeded":
                    raise ValueError("Chưa có kết quả thành công.")
                self.respond(table_data(folder / "result/part-r-00000", **self.pagination(url)))
            elif parts[4] == "input":
                self.respond(table_data(folder / "input.csv", **self.pagination(url)))
            elif parts[4] == "download":
                if job["status"] != "succeeded":
                    raise ValueError("Chưa có kết quả thành công.")
                self.respond((folder / "result/part-r-00000").read_bytes(), "text/csv; charset=utf-8", attachment="orders-%s.csv" % ident[:8])
            elif parts[4] == "log":
                self.respond((folder / "job.log").read_bytes() if (folder / "job.log").exists() else b"", "text/plain; charset=utf-8", attachment="hadoop-%s.log" % ident[:8])
            elif parts[4] == "report":
                self.respond(json.dumps(job, ensure_ascii=False, indent=2).encode("utf-8"), attachment="run-%s.json" % ident[:8])
            else:
                self.respond(dict(error="Không tìm thấy tài nguyên."), status=404)
        else:
            relative = "index.html" if path == "/" else path.lstrip("/")
            file = (ROOT / "ui" / relative).resolve()
            if not file.is_relative_to((ROOT / "ui").resolve()) or not file.is_file():
                self.respond(dict(error="Không tìm thấy trang."), status=404)
                return
            self.respond(file.read_bytes(), mimetypes.guess_type(file)[0] or "application/octet-stream")

    def pagination(self, url):
        query = parse_qs(url.query)
        page = int(query.get("page", ["1"])[0])
        if page < 1:
            raise ValueError("Trang không hợp lệ.")
        return dict(page=page, search=query.get("search", [""])[0][:200])

    def do_POST(self):
        try:
            origin = self.headers.get("Origin")
            if origin and origin not in ("http://127.0.0.1:%d" % self.server.server_port,
                                         "http://localhost:%d" % self.server.server_port):
                self.respond(dict(error="Origin không hợp lệ."), status=403)
                return
            length = int(self.headers.get("Content-Length", "0"))
            if not 0 < length <= MAX_UPLOAD * 2:
                raise ValueError("Kích thước yêu cầu không hợp lệ.")
            data = json.loads(self.rfile.read(length))
            if self.path == "/api/uploads":
                self.respond(self.server.workspace.upload(data), status=201)
            elif self.path == "/api/jobs":
                self.respond(self.server.workspace.submit(data), status=201)
            elif re.fullmatch(r"/api/jobs/[a-f0-9]{32}/cancel", self.path):
                self.respond(self.server.workspace.cancel(self.path.split("/")[3]))
            else:
                self.respond(dict(error="Không tìm thấy tài nguyên."), status=404)
        except (ValueError, KeyError) as exception:
            self.respond(dict(error=str(exception)), status=400)
        except Exception as exception:
            self.respond(dict(error=str(exception)), status=500)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()
    STORE.mkdir(parents=True, exist_ok=True)
    instance_lock = (STORE / ".server.lock").open("a+")
    try:
        fcntl.flock(instance_lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
    except BlockingIOError:
        info = STORE / ".server-info.json"
        message = json.loads(info.read_text(encoding="utf-8"))["url"] if info.exists() else "Đang khởi động"
        print("Giao diện đã chạy: " + message, flush=True)
        instance_lock.close()
        return
    workspace = Workspace()
    server = None
    for port in range(args.port, args.port + 20):
        try:
            server = ThreadingHTTPServer(("127.0.0.1", port), Handler)
            break
        except OSError:
            continue
    if server is None:
        raise RuntimeError("Không tìm được cổng trống.")
    server.workspace = workspace
    url = "http://127.0.0.1:%d" % server.server_port
    write_json(STORE / ".server-info.json", dict(url=url, pid=os.getpid()))
    print("Giao diện Bài 20: " + url, flush=True)
    signal.signal(signal.SIGTERM, lambda *_: (_ for _ in ()).throw(KeyboardInterrupt()))
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        workspace.stop()
        server.server_close()
        instance_lock.close()


if __name__ == "__main__":
    main()
