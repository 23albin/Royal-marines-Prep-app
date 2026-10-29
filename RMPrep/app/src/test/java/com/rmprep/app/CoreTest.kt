package com.rmprep.app
import org.junit.Assert.*
import org.junit.Test
import java.time.YearMonth

class CoreTest {
    @Test fun toggleSavesAndRemoves() {
        val d = AppData().toggle("2026-01-05", 1).toggle("2026-01-05", 2)
        assertEquals(setOf(1, 2), d.checks["2026-01-05"]); assertTrue(d.toggle("2026-01-05", 1).toggle("2026-01-05", 2).checks.isEmpty())
    }
    @Test fun percent() { assertEquals(60, Stats.percent(3, 5)); assertEquals(0, Stats.percent(0, 0)); assertEquals(100, Stats.percent(9, 5)) }
    @Test fun leapYears() {
        assertEquals(29, Stats.monthGrid(YearMonth.of(2028, 2)).count { it != null })
        assertEquals(28, Stats.monthGrid(YearMonth.of(2026, 2)).count { it != null })
        assertEquals(28, Stats.monthGrid(YearMonth.of(2100, 2)).count { it != null })
    }
    @Test fun gridStartsMonday() { assertEquals(2, Stats.monthGrid(YearMonth.of(2026, 7)).takeWhile { it == null }.size) }
    @Test fun roundTrip() {
        val d = AppData(mapOf("2026-01-05" to setOf(0, 2)), setOf("2026-01-05"), listOf(TestRecord("a", "2026-01-06", 10, null, 1.2, null, null)))
        assertEquals(d, Codec.decode(Codec.encode(d)).getOrThrow())
    }
    @Test fun malformedRejected() {
        listOf("", "{}", "not json", """{"app":"RMPrep","version":1,"checks":{"bad":[1]},"done":[],"tests":[]}""",
            """{"app":"RMPrep","version":9,"checks":{},"done":[],"tests":[]}""").forEach { assertTrue(it, Codec.decode(it).isFailure) }
    }
    @Test fun editDeleteNoDuplicates() {
        val t = TestRecord("a", "2026-01-06", 10, null, null, null, null)
        var d = AppData().upsertTest(t).upsertTest(t.copy(pushUps = 12))
        assertEquals(1, d.tests.size); assertEquals(12, d.tests[0].pushUps); assertTrue(d.deleteTest("a").tests.isEmpty())
    }
}
