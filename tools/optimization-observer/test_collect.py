#!/usr/bin/env python3
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import collect


def response(stdout='', code=0):
    return {'returncode': code, 'stdout': stdout, 'stderr': '', 'elapsed_seconds': 0}


class TestObserver(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.output = Path(self.tmp.name) / 'result'
        self.args = ['--serial', 'device-123', '--output', str(self.output),
                     '--samples', '2', '--interval', '1']

    def test_read_only_sampling_and_no_overwrite(self):
        calls = []
        def fake(serial, *command):
            calls.append(command)
            if command == ('get-state',):
                return response('device\n')
            if command[:2] == ('shell', 'pidof'):
                return response('123\n')
            return response('example device-123\n')
        with patch.object(collect, 'invoke', side_effect=fake), patch.object(collect.time, 'sleep'):
            self.assertEqual(collect.main(self.args), 0)
            data = json.loads((self.output / 'observations.json').read_text())
            self.assertEqual(len(data['samples']), 2)
            self.assertTrue(data['complete'])
            self.assertNotIn('device-123', json.dumps(data))
            self.assertTrue(all(c == ('get-state',) or c[:2] in [('shell', 'pidof'), ('shell', 'dumpsys')] for c in calls))
            original = (self.output / 'observations.json').read_bytes()
            self.assertEqual(collect.main(self.args), 1)
            self.assertEqual(original, (self.output / 'observations.json').read_bytes())

    def test_numeric_serial_does_not_break_pid_detection(self):
        def fake(serial, *command):
            if command == ('get-state',): return response('device')
            if command[:2] == ('shell', 'pidof'): return response('1234')
            return response('ok')
        args = self.args.copy()
        args[1] = '123'
        with patch.object(collect, 'invoke', side_effect=fake), patch.object(collect.time, 'sleep'):
            self.assertEqual(collect.main(args), 0)
        data = json.loads((self.output / 'observations.json').read_text())
        self.assertEqual(data['samples'][0]['pids'], [1234])

    def test_pid_change_stops_incomplete(self):
        count = 0
        def fake(serial, *command):
            nonlocal count
            if command == ('get-state',):
                return response('device')
            if command[:2] == ('shell', 'pidof'):
                count += 1
                return response('42' if count < 3 else '84')
            return response('ok')
        with patch.object(collect, 'invoke', side_effect=fake), patch.object(collect.time, 'sleep'):
            self.assertEqual(collect.main(self.args), 1)
        data = json.loads((self.output / 'observations.json').read_text())
        self.assertFalse(data['complete'])
        self.assertEqual(len(data['samples']), 1)
        self.assertEqual(data['samples'][0]['end_pids'], [84])

    def test_unavailable_or_missing_process_creates_no_output(self):
        for results in ([response('', 1)], [response('device'), response('', 1)]):
            with patch.object(collect, 'invoke', side_effect=results):
                self.assertEqual(collect.main(self.args), 1)
                self.assertFalse(self.output.exists())

    def test_failure_report_is_incomplete(self):
        def fake(serial, *command):
            if command == ('get-state',): return response('device')
            if command[:2] == ('shell', 'pidof'): return response('42')
            if command[1:] == ('dumpsys', 'thermalservice'): return response('', 1)
            return response('ok')
        with patch.object(collect, 'invoke', side_effect=fake), patch.object(collect.time, 'sleep'):
            self.assertEqual(collect.main(self.args), 1)
        self.assertFalse(json.loads((self.output / 'observations.json').read_text())['complete'])

    def test_invalid_arguments(self):
        for extra in (['--samples', '0'], ['--interval', 'nan'], ['--package', 'bad;input']):
            with self.assertRaises(SystemExit):
                collect.main(self.args + extra)
        self.assertFalse(self.output.exists())


if __name__ == '__main__':
    unittest.main()
