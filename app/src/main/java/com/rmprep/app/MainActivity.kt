package com.rmprep.app
import android.app.Application
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

val Lime = Color(0xFFB6F23C); val Olive = Color(0xFF4B5D3A); val Card = Color(0xFF171A15); val Grey = Color(0xFF9AA392)

class VM(app: Application) : AndroidViewModel(app) {
    private val file = File(app.filesDir, "data.json")
    var data by mutableStateOf(load()); private set
    private fun load() = if (file.exists()) Codec.decode(file.readText()).getOrDefault(AppData()) else AppData()
    fun commit(d: AppData) { data = d; val tmp = File(file.path + ".tmp"); tmp.writeText(Codec.encode(d)); tmp.renameTo(file) }
    fun toggle(date: LocalDate, i: Int) = commit(data.toggle(date.toString(), i))
    fun setDone(date: LocalDate, v: Boolean) = commit(data.withDone(date.toString(), v))
    fun save(t: TestRecord) = commit(data.upsertTest(t))
    fun delete(id: String) = commit(data.deleteTest(id))
    fun import(s: String): Result<Unit> = Codec.decode(s).map { commit(it) }
    fun clear() = commit(AppData())
}

class MainActivity : ComponentActivity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        setContent { MaterialTheme(colorScheme = darkColorScheme(primary = Lime, onPrimary = Color.Black, secondary = Olive,
            background = Color(0xFF0D0F0C), surface = Card, onBackground = Color.White, onSurface = Color.White)) { App() } }
    }
}

@Composable fun App(vm: VM = viewModel()) {
    var tab by remember { mutableIntStateOf(0) }
    var date by remember { mutableStateOf(LocalDate.now()) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = {
        NavigationBar(containerColor = Card) {
            listOf("Today", "Calendar", "Progress", "Plan").forEachIndexed { i, n ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(listOf("●", "▦", "↗", "☰")[i]) }, label = { Text(n) },
                    colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.Black, indicatorColor = Lime)) } }
    }) { pad -> Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState())) {
        when (tab) {
            0 -> TodayScreen(vm, date) { date = it }
            1 -> CalendarScreen(vm, date) { date = it; tab = 0 }
            2 -> ProgressScreen(vm)
            else -> PlanScreen(vm)
        } } }
}

@Composable fun Box2(content: @Composable ColumnScope.() -> Unit) = Surface(color = Card, shape = RoundedCornerShape(16.dp),
    border = BorderStroke(1.dp, Olive), modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) { Column(Modifier.padding(16.dp), content = content) }

@Composable fun TodayScreen(vm: VM, date: LocalDate, setDate: (LocalDate) -> Unit) {
    val day = Plan.forDate(date); val key = date.toString()
    val checked = vm.data.checks[key] ?: emptySet()
    val pct = Stats.percent(checked.count { it < day.exercises.size }, day.exercises.size)
    var confirm by remember { mutableStateOf(false) }
    val isDone = key in vm.data.done
    Text("RM Prep", color = Lime, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { setDate(date.minusDays(1)) }, Modifier.semantics { contentDescription = "Previous day" }) { Text("‹", fontSize = 28.sp) }
        Text(date.format(DateTimeFormatter.ofPattern("EEEE d MMMM yyyy")), Modifier.weight(1f), fontSize = 16.sp)
        TextButton(onClick = { setDate(date.plusDays(1)) }, Modifier.semantics { contentDescription = "Next day" }) { Text("›", fontSize = 28.sp) }
    }
    if (date != LocalDate.now()) TextButton(onClick = { setDate(LocalDate.now()) }) { Text("Back to today") }
    Box2 {
        Text(day.title, fontSize = 22.sp, fontWeight = FontWeight.Bold); Text(day.intro, color = Grey)
        Spacer(Modifier.height(8.dp))
        day.exercises.forEachIndexed { i, ex ->
            Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).toggleable(i in checked, role = Role.Checkbox) { vm.toggle(date, i) },
                verticalAlignment = Alignment.CenterVertically) {
                Checkbox(i in checked, null, colors = CheckboxDefaults.colors(checkedColor = Lime, checkmarkColor = Color.Black))
                Spacer(Modifier.width(8.dp)); Text(ex, fontSize = 16.sp) } }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(progress = { pct / 100f }, Modifier.fillMaxWidth().height(8.dp), color = Lime, trackColor = Olive)
        Text("$pct% of exercises ticked", color = Grey, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(8.dp))
        Button(onClick = { if (isDone) vm.setDone(date, false) else confirm = true }, Modifier.fillMaxWidth().height(52.dp)) {
            Text(if (isDone) "Workout complete ✓ (tap to undo)" else "Mark workout complete") }
    }
    Box2 {
        Text("This week", fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Stats.weekOf(date).forEach { d -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(d.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()), color = Grey)
                Box(Modifier.size(32.dp).background(if (d.toString() in vm.data.done) Lime else Olive, CircleShape)
                    .semantics { contentDescription = "${d.dayOfWeek} ${if (d.toString() in vm.data.done) "completed" else "not completed"}" }) } } }
        Spacer(Modifier.height(8.dp))
        Text("Workouts completed: ${vm.data.done.size}   Exercises ticked: ${vm.data.checks.values.sumOf { it.size }}", color = Grey)
    }
    Text("Tired, unwell or in pain? Rest today and tell a parent or trusted adult.", color = Grey, modifier = Modifier.padding(8.dp))
    if (confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Finished this workout?") },
        text = { Text("Only mark it complete if you actually did it.") },
        confirmButton = { TextButton({ vm.setDone(date, true); confirm = false }) { Text("Yes, complete") } },
        dismissButton = { TextButton({ confirm = false }) { Text("Not yet") } })
}

@Composable fun CalendarScreen(vm: VM, selected: LocalDate, open: (LocalDate) -> Unit) {
    var ym by remember { mutableStateOf(YearMonth.from(selected)) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton({ ym = ym.minusMonths(1) }, Modifier.semantics { contentDescription = "Previous month" }) { Text("‹", fontSize = 28.sp) }
        Text("${ym.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${ym.year}", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Bold)
        TextButton({ ym = ym.plusMonths(1) }, Modifier.semantics { contentDescription = "Next month" }) { Text("›", fontSize = 28.sp) }
    }
    Row(Modifier.fillMaxWidth()) { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, Modifier.weight(1f), color = Grey, textAlign = androidx.compose.ui.text.style.TextAlign.Center) } }
    Stats.monthGrid(ym).chunked(7).forEach { week ->
        Row(Modifier.fillMaxWidth()) {
            (week + List(7 - week.size) { null }).forEach { d ->
                if (d == null) Spacer(Modifier.weight(1f).aspectRatio(1f)) else {
                    val done = d.toString() in vm.data.done; val part = !done && !vm.data.checks[d.toString()].isNullOrEmpty()
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp)
                        .background(if (d == selected) Olive else Card, RoundedCornerShape(10.dp))
                        .border(if (d == LocalDate.now()) 2.dp else 0.dp, Lime, RoundedCornerShape(10.dp))
                        .clickable { open(d) }.semantics { contentDescription = "${d} ${if (done) "completed" else if (part) "partly done" else "planned"}" },
                        contentAlignment = Alignment.Center) {
                        Text("${d.dayOfMonth}")
                        if (done || part) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp).size(7.dp).background(if (done) Lime else Grey, CircleShape)) } } } } }
    Text("Lime dot = completed, grey dot = partly ticked. Tap a date to open its checklist.", color = Grey, modifier = Modifier.padding(top = 8.dp))
}

@Composable fun ProgressScreen(vm: VM) {
    var editId by remember { mutableStateOf<String?>(null) }
    var dateS by remember { mutableStateOf(LocalDate.now().toString()) }
    var push by remember { mutableStateOf("") }; var pull by remember { mutableStateOf("") }; var miles by remember { mutableStateOf("") }
    var mins by remember { mutableStateOf("") }; var swim by remember { mutableStateOf("") }; var err by remember { mutableStateOf<String?>(null) }
    val tests = vm.data.tests
    Text("Progress", fontSize = 22.sp, fontWeight = FontWeight.Bold)
    val last = tests.lastOrNull()
    if (last == null) Box2 { Text("No fitness tests yet. Add your first test below — nothing is shown until you record it.", color = Grey) }
    else Box2 {
        Text("Latest test: ${last.date}", fontWeight = FontWeight.Bold)
        Text("Push-ups ${last.pushUps ?: "–"} · Pull-ups ${last.pullUps ?: "–"} · 9-min run ${last.runMiles ?: "–"} mi")
        val days = ChronoUnit.DAYS.between(LocalDate.parse(last.date), LocalDate.now())
        if (days >= 28) Text("It's been $days days — time to retest.", color = Lime)
        if (tests.size >= 2) { val p = tests[tests.size - 2]
            fun dl(a: Int?, b: Int?) = if (a == null || b == null) "–" else (a - b).let { if (it > 0) "+$it" else "$it" }
            Text("Change since previous: push-ups ${dl(last.pushUps, p.pushUps)}, pull-ups ${dl(last.pullUps, p.pullUps)}", color = Grey) }
    }
    Box2 {
        Text(if (editId == null) "Add test" else "Edit test", fontWeight = FontWeight.Bold)
        OutlinedTextField(dateS, { dateS = it }, label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(push, { push = it }, label = { Text("Max strict push-ups") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(pull, { pull = it }, label = { Text("Max strict pull-ups") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(miles, { miles = it }, label = { Text("Miles run in 9 minutes") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(mins, { mins = it }, label = { Text("Continuous run, minutes (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        OutlinedTextField(swim, { swim = it }, label = { Text("Swim, metres (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        err?.let { Text(it, color = Color(0xFFFF8A80)) }
        Button(onClick = {
            try {
                val d = LocalDate.parse(dateS.trim()).toString()
                fun i(s: String) = s.trim().ifEmpty { null }?.toInt()?.also { require(it >= 0) }
                val m = miles.trim().ifEmpty { null }?.toDouble()?.also { require(it >= 0) }
                val r = TestRecord(editId ?: UUID.randomUUID().toString(), d, i(push), i(pull), m, i(mins), i(swim))
                if (r.pushUps == null && r.pullUps == null && r.runMiles == null && r.runMins == null && r.swimM == null) err = "Enter at least one result."
                else { vm.save(r); err = null; editId = null; push = ""; pull = ""; miles = ""; mins = ""; swim = "" }
            } catch (e: Exception) { err = "Check the date (YYYY-MM-DD) and use whole non-negative numbers." }
        }, Modifier.fillMaxWidth().height(48.dp)) { Text("Save test") }
    }
    tests.reversed().forEach { t -> Box2 {
        Text(t.date, fontWeight = FontWeight.Bold)
        Text("Push ${t.pushUps ?: "–"} · Pull ${t.pullUps ?: "–"} · Run ${t.runMiles ?: "–"} mi · ${t.runMins ?: "–"} min · Swim ${t.swimM ?: "–"} m", color = Grey)
        Row { TextButton({ editId = t.id; dateS = t.date; push = t.pushUps?.toString() ?: ""; pull = t.pullUps?.toString() ?: ""
            miles = t.runMiles?.toString() ?: ""; mins = t.runMins?.toString() ?: ""; swim = t.swimM?.toString() ?: "" }) { Text("Edit") }
            TextButton({ vm.delete(t.id) }) { Text("Delete") } } } }
}

@Composable fun PlanScreen(vm: VM) {
    val ctx = LocalContext.current; val uri = LocalUriHandler.current
    var wipe by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u: Uri? ->
        if (u != null) try { ctx.contentResolver.openOutputStream(u)!!.use { it.write(Codec.encode(vm.data).toByteArray()) }; Toast.makeText(ctx, "Backup saved", Toast.LENGTH_SHORT).show() }
        catch (e: Exception) { Toast.makeText(ctx, "Could not save backup", Toast.LENGTH_LONG).show() } }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
        if (u != null) { val s = try { ctx.contentResolver.openInputStream(u)!!.use { String(it.readBytes()) } } catch (e: Exception) { "" }
            vm.import(s).fold({ Toast.makeText(ctx, "Backup restored", Toast.LENGTH_SHORT).show() }, { Toast.makeText(ctx, it.message, Toast.LENGTH_LONG).show() }) } }
    Text("Plan and Safety", fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Box2 { Text("Weekly schedule", fontWeight = FontWeight.Bold)
        Plan.days.forEach { (d, p) -> Text("${d.getDisplayName(TextStyle.SHORT, Locale.getDefault())}: ${p.title}", Modifier.padding(vertical = 3.dp)) } }
    Box2 { Text("Milestones (suggested personal targets)", fontWeight = FontWeight.Bold)
        val bp = vm.data.tests.maxOfOrNull { it.pushUps ?: 0 } ?: 0; val bu = vm.data.tests.maxOfOrNull { it.pullUps ?: 0 } ?: 0
        listOf(Triple("Initial: 15–20 push-ups, 2–3 pull-ups", 15, 2), Triple("Next: 20–25 push-ups, 3–5 pull-ups", 20, 3),
            Triple("Intermediate: 30+ push-ups, 5+ pull-ups", 30, 5), Triple("Longer-term: 40+ push-ups, 8+ pull-ups", 40, 8)).forEach { (t, a, b) ->
            Text((if (vm.data.tests.isNotEmpty() && bp >= a && bu >= b) "✓ " else "○ ") + t, Modifier.padding(vertical = 3.dp)) }
        Text("Everyone progresses differently. Technique matters more than numbers.", color = Grey) }
    Box2 { Text("Safety", fontWeight = FontWeight.Bold)
        Text("RM Prep is an independent training planner, not an official recruitment assessment or endorsed product. Never train through pain, illness or exhaustion. Stay hydrated and eat properly. Swim only with supervision. Tell a parent or trusted adult if something hurts or worries you.", color = Grey)
        TextButton({ uri.openUri("https://www.royalnavy.mod.uk/careers/royal-marines") }) { Text("Official Royal Navy careers site") } }
    Box2 { Text("Your data (stored only on this phone)", fontWeight = FontWeight.Bold)
        Button({ export.launch("rmprep-backup-${LocalDate.now()}.json") }, Modifier.fillMaxWidth()) { Text("Export backup") }
        OutlinedButton({ import.launch(arrayOf("application/json", "text/plain", "*/*")) }, Modifier.fillMaxWidth()) { Text("Import backup") }
        TextButton({ wipe = true }) { Text("Delete all data", color = Color(0xFFFF8A80)) } }
    if (wipe) AlertDialog(onDismissRequest = { wipe = false }, title = { Text("Delete everything?") }, text = { Text("This permanently removes all workouts and test results.") },
        confirmButton = { TextButton({ vm.clear(); wipe = false }) { Text("Delete") } }, dismissButton = { TextButton({ wipe = false }) { Text("Cancel") } })
}
