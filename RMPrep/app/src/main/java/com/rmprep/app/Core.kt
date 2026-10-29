package com.rmprep.app
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

data class TestRecord(val id: String, val date: String, val pushUps: Int?, val pullUps: Int?,
                      val runMiles: Double?, val runMins: Int?, val swimM: Int?)
data class AppData(val checks: Map<String, Set<Int>> = emptyMap(), val done: Set<String> = emptySet(),
                   val tests: List<TestRecord> = emptyList())

fun AppData.toggle(date: String, idx: Int): AppData {
    val cur = checks[date] ?: emptySet()
    val next = if (idx in cur) cur - idx else cur + idx
    return copy(checks = if (next.isEmpty()) checks - date else checks + (date to next))
}
fun AppData.withDone(date: String, v: Boolean) = copy(done = if (v) done + date else done - date)
fun AppData.upsertTest(t: TestRecord) = copy(tests = (tests.filter { it.id != t.id } + t).sortedBy { it.date })
fun AppData.deleteTest(id: String) = copy(tests = tests.filter { it.id != id })

object Stats {
    fun percent(checked: Int, total: Int) = if (total <= 0) 0 else (checked.coerceIn(0, total) * 100) / total
    /** Monday-first grid; null = blank cell. */
    fun monthGrid(ym: YearMonth): List<LocalDate?> {
        val lead = ym.atDay(1).dayOfWeek.value - 1
        return List(lead) { null } + (1..ym.lengthOfMonth()).map { ym.atDay(it) }
    }
    fun weekOf(d: LocalDate): List<LocalDate> { val m = d.with(DayOfWeek.MONDAY); return (0..6).map { m.plusDays(it.toLong()) } }
}

object Codec {
    const val VERSION = 1
    fun encode(d: AppData): String = JSONObject().apply {
        put("app", "RMPrep"); put("version", VERSION)
        put("checks", JSONObject().also { o -> d.checks.forEach { (k, v) -> o.put(k, JSONArray(v.sorted())) } })
        put("done", JSONArray(d.done.sorted()))
        put("tests", JSONArray().also { a -> d.tests.forEach { t -> a.put(JSONObject().apply {
            put("id", t.id); put("date", t.date)
            t.pushUps?.let { put("pushUps", it) }; t.pullUps?.let { put("pullUps", it) }
            t.runMiles?.let { put("runMiles", it) }; t.runMins?.let { put("runMins", it) }; t.swimM?.let { put("swimM", it) } }) } })
    }.toString(2)

    /** Validates strictly; any problem returns a failure with a readable message. */
    fun decode(s: String): Result<AppData> = try {
        require(s.isNotBlank()) { "The file is empty." }
        val o = JSONObject(s)
        require(o.optString("app") == "RMPrep") { "This is not an RM Prep backup." }
        require(o.optInt("version", 0) in 1..VERSION) { "Unsupported backup version." }
        val checks = HashMap<String, Set<Int>>()
        val co = o.getJSONObject("checks")
        co.keys().forEach { k -> LocalDate.parse(k); val a = co.getJSONArray(k)
            checks[k] = (0 until a.length()).map { i -> a.getInt(i).also { n -> require(n in 0..99) { "Bad exercise index." } } }.toSet() }
        val da = o.getJSONArray("done")
        val done = (0 until da.length()).map { LocalDate.parse(da.getString(it)).toString() }.toSet()
        val ta = o.getJSONArray("tests")
        val tests = (0 until ta.length()).map { i -> val t = ta.getJSONObject(i)
            fun oi(k: String) = if (t.has(k)) t.getInt(k).also { v -> require(v >= 0) { "Negative value." } } else null
            TestRecord(t.getString("id"), LocalDate.parse(t.getString("date")).toString(), oi("pushUps"), oi("pullUps"),
                if (t.has("runMiles")) t.getDouble("runMiles").also { v -> require(v >= 0) { "Negative value." } } else null,
                oi("runMins"), oi("swimM")) }.distinctBy { it.id }.sortedBy { it.date }
        Result.success(AppData(checks, done, tests))
    } catch (e: Exception) { Result.failure(IllegalArgumentException("Invalid backup: ${e.message ?: "unreadable file"}")) }
}

data class Day(val title: String, val intro: String, val exercises: List<String>)
object Plan {
    private val warm = "Warm up 5–10 minutes"
    val days: Map<DayOfWeek, Day> = mapOf(
        DayOfWeek.MONDAY to Day("Easy run and strength", "Keep the run conversational.", listOf(warm, "Easy run/walk 15–20 min", "Push-ups: 3 sets of 5–7", "Squats: 3 sets of 10", "Cool down")),
        DayOfWeek.TUESDAY to Day("Strength and pull-ups", "Slow, controlled reps.", listOf(warm, "Push-ups: 3 sets of 5–7", "Assisted pull-ups: 3 easy sets", "Squats: 3 sets of 10", "Plank: 3 × 20–30 s")),
        DayOfWeek.WEDNESDAY to Day("Easy cardio", "Brisk walk, easy cycle or supervised swim.", listOf("20–30 min easy cardio", "Optional gentle mobility")),
        DayOfWeek.THURSDAY to Day("Strength and pull-ups", "Quality over quantity.", listOf(warm, "Push-ups: 3 sets of 5–7", "Assisted pull-ups: 3 easy sets", "Lunges: 3 sets of 8–10 per leg", "Plank: 3 × 20–30 s")),
        DayOfWeek.FRIDAY to Day("Easy run/walk", "Comfortable running and walking.", listOf(warm, "Run/walk 15–20 min", "Cool down")),
        DayOfWeek.SATURDAY to Day("Optional activity", "Easy sport, cycling or supervised swim. Extra recovery is fine.", listOf("20–30 min easy activity")),
        DayOfWeek.SUNDAY to Day("Rest and recovery", "Rest is part of training.", listOf("Rest", "Optional gentle walk or mobility", "Prepare for next week")))
    fun forDate(d: LocalDate) = days.getValue(d.dayOfWeek)
}
