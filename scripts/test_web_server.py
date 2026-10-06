import base64
import json
from pathlib import Path
import tempfile
import threading
import unittest
from urllib.error import HTTPError
from urllib.request import Request, urlopen

from experiments import DEFAULTS, oracle
from web_server import Handler, ThreadingHTTPServer, Workspace, parse_rules, parsed_line, table_data


class WebTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temporary = tempfile.TemporaryDirectory()
        cls.workspace = Workspace(Path(cls.temporary.name))
        cls.server = ThreadingHTTPServer(("127.0.0.1", 0), Handler)
        cls.server.workspace = cls.workspace
        cls.thread = threading.Thread(target=cls.server.serve_forever, daemon=True)
        cls.thread.start()
        cls.url = "http://127.0.0.1:%d" % cls.server.server_port

    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown()
        cls.server.server_close()
        cls.thread.join()
        cls.temporary.cleanup()

    def test_rules_match_java_contract(self):
        self.assertEqual(parse_rules(DEFAULTS), DEFAULTS)
        for changes in [dict(min_amount="2000001"), dict(start_date="2026-10-01"),
                        dict(min_orders=0), dict(min_orders="1.5"), dict(min_orders=2147483648),
                        dict(min_total="1e6"), dict(categories="Books,"), dict(end_date="2026-09-31")]:
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                parse_rules(dict(DEFAULTS, **changes))

    def test_csv_preview_handles_invalid_rows(self):
        row, error = parsed_line('"A,1",C,2026-09-01,1000000.10,Books,COMPLETED')
        self.assertIsNone(error)
        self.assertEqual(row[0], "A,1")
        for line in ['"unclosed', 'A,C,2026-09-31,1000000,Books,COMPLETED',
                     'A,C,2026-09-01,1e6,Books,COMPLETED', 'A,C']:
            self.assertIsNotNone(parsed_line(line)[1])

    def test_oracle_keeps_cents_for_large_money(self):
        path = Path(self.temporary.name) / "large-money.csv"
        amount = "10000000000000000000000000000.01"
        path.write_text(("A,C,2026-09-01," + amount + ",Books,COMPLETED\n") * 2,
                        encoding="utf-8")
        rules = dict(DEFAULTS, min_amount="0", max_amount=amount, min_orders=2,
                     min_total="20000000000000000000000000000.02")
        self.assertEqual(parse_rules(rules), rules)
        self.assertEqual(oracle(path, rules)[0]["OUTPUT_RECORDS"], 2)
        rules["min_total"] = "20000000000000000000000000000.03"
        self.assertEqual(oracle(path, rules)[0]["OUTPUT_RECORDS"], 0)

    def test_pagination_search_and_duplicate_preservation(self):
        path = Path(self.temporary.name) / "rows.csv"
        rows = ["A,C,2026-09-01,1000000,Books,COMPLETED"] * 27
        path.write_text("\n".join(rows) + "\n", encoding="utf-8")
        second = table_data(path, page=2)
        self.assertEqual((second["total"], second["pages"], len(second["rows"])), (27, 2, 2))
        self.assertEqual(table_data(path, search="absent")["matched"], 0)

    def test_upload_stays_inside_store_and_survives_reload(self):
        raw = b"A,C,2026-09-01,1000000,Books,COMPLETED\n"
        entry = self.workspace.upload(dict(name="../../my.csv", content=base64.b64encode(raw).decode()))
        self.assertEqual(entry["name"], "my.csv")
        path = self.workspace.dataset(entry["id"])[1]
        self.assertTrue(path.is_relative_to(self.workspace.store))
        reloaded = Workspace(self.workspace.store)
        self.assertEqual(reloaded.dataset(entry["id"])[0]["records"], 1)
        for name, content in [("wrong.txt", raw), ("empty.csv", b""),
                              ("bom.csv", b"\xef\xbb\xbf" + raw), ("bad.csv", b"\xff")]:
            with self.subTest(name=name), self.assertRaises(ValueError):
                self.workspace.upload(dict(name=name, content=base64.b64encode(content).decode()))

    def test_http_validates_origin_and_rules(self):
        with urlopen(self.url + "/api/datasets") as response:
            self.assertGreaterEqual(len(json.load(response)), 4)
        for payload, headers, expected in [
            (dict(dataset="sample", rules=dict(DEFAULTS, min_orders=0)), {}, 400),
            ({}, {"Origin": "http://external.invalid"}, 403),
        ]:
            request = Request(self.url + "/api/jobs", data=json.dumps(payload).encode(),
                              headers=dict({"Content-Type": "application/json"}, **headers))
            with self.assertRaises(HTTPError) as raised:
                urlopen(request)
            self.assertEqual(raised.exception.code, expected)


if __name__ == "__main__":
    unittest.main()
