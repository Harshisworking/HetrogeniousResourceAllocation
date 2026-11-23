"""Standalone test: PYTHONPATH=. python3 -m unittest discover -s tests -p test_finalizer.py"""
import tempfile
import unittest
from pathlib import Path
import importlib.util

module_path = Path(__file__).resolve().parents[1]/'scripts'/'finalize_results.py'
spec = importlib.util.spec_from_file_location('batch3_finalizer',module_path)
mod = importlib.util.module_from_spec(spec)
spec.loader.exec_module(mod)

class FinalizerTests(unittest.TestCase):
    def test_valid(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/'a.log'
            p.write_text('successful_requests: 100\nfailed_requests: 0\nclient_p95_ms: 17.5\nactual_elapsed_seconds: 20\n')
            v=mod.parse_traffic(p)
            self.assertEqual(v['successful_rps'],5)
    def test_refuse_incomplete(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/'a.log';p.write_text('successful_requests: 100\n')
            with self.assertRaises(ValueError):mod.parse_traffic(p)
    def test_readiness_before_event(self):
        with tempfile.TemporaryDirectory() as tmp:
            p=Path(tmp)/'readiness.log'
            p.write_text('===== 2026-11-23T01:39:35Z =====\nportal-amd64  1/1  1  1  6h\n===== 2026-11-23T01:39:47Z =====\nportal-amd64  3/3  3  3  6h\n')
            r=mod.parse_readiness(p,'2026-11-23T01:41:36Z')
            self.assertTrue(r['ready_3_before_event'])
            self.assertEqual(r['three_ready_lead_seconds'],109)

if __name__=='__main__':unittest.main()
