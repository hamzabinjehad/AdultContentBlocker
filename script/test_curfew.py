"""The curfew's window arithmetic and allow-list decisions, without touching the Mac."""
import unittest

import curfew


class ClockTests(unittest.TestCase):
    def test_parses_the_ways_a_person_writes_a_time(self):
        self.assertEqual(curfew.parse_clock("22:00"), 1320)
        self.assertEqual(curfew.parse_clock("4"), 240)
        self.assertEqual(curfew.parse_clock("04:00"), 240)
        self.assertEqual(curfew.parse_clock("00:30"), 30)

    def test_rejects_nonsense(self):
        for value in ("", "25:00", "22:61", "ten", "24:30"):
            with self.assertRaises(Exception):
                curfew.parse_clock(value)

    def test_clock_text_round_trips(self):
        self.assertEqual(curfew.clock_text(curfew.parse_clock("22:00")), "22:00")
        self.assertEqual(curfew.clock_text(curfew.parse_clock("4:05")), "04:05")


class WindowTests(unittest.TestCase):
    def test_overnight_window(self):
        start, end = curfew.parse_clock("22:00"), curfew.parse_clock("04:00")
        self.assertTrue(curfew.in_window(22 * 60, start, end))
        self.assertTrue(curfew.in_window(23 * 60 + 59, start, end))
        self.assertTrue(curfew.in_window(0, start, end))
        self.assertTrue(curfew.in_window(3 * 60 + 59, start, end))
        self.assertFalse(curfew.in_window(4 * 60, start, end))
        self.assertFalse(curfew.in_window(21 * 60 + 59, start, end))
        self.assertFalse(curfew.in_window(12 * 60, start, end))

    def test_same_day_window(self):
        start, end = curfew.parse_clock("09:00"), curfew.parse_clock("17:00")
        self.assertTrue(curfew.in_window(12 * 60, start, end))
        self.assertFalse(curfew.in_window(8 * 60 + 59, start, end))
        self.assertFalse(curfew.in_window(17 * 60, start, end))

    def test_empty_window_never_matches(self):
        self.assertFalse(curfew.in_window(22 * 60, 1320, 1320))


class AllowTests(unittest.TestCase):
    def test_implicit_system_apps_always_pass(self):
        for bundle in curfew.IMPLICIT_ALLOW:
            self.assertTrue(curfew.is_allowed(bundle, []), bundle)

    def test_unlisted_app_is_blocked(self):
        self.assertFalse(curfew.is_allowed("com.google.Chrome", []))
        self.assertTrue(curfew.is_allowed("com.google.Chrome", ["com.google.Chrome"]))

    def test_unreadable_front_app_does_not_blank(self):
        self.assertTrue(curfew.is_allowed(None, []))


class PlistTests(unittest.TestCase):
    def test_agent_runs_every_ten_seconds(self):
        plist = curfew.make_plist(curfew.Path("/tmp/curfew.py"))
        self.assertEqual(plist["Label"], curfew.LABEL)
        self.assertEqual(plist["StartInterval"], 10)
        self.assertEqual(plist["ProgramArguments"][-1], "tick")
        self.assertTrue(plist["RunAtLoad"])


if __name__ == "__main__":
    unittest.main()
