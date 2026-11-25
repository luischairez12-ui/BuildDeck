package com.builddeck.mobile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal val NovaBlue = Color(0xFF1159F5)
internal val NovaBlueDeep = Color(0xFF0D3DB8)
internal val NovaInk = Color(0xFF102142)
internal val NovaMuted = Color(0xFF65758F)
internal val NovaBg = Color(0xFFF5F8FF)
internal val NovaLine = Color(0xFFE1EAF8)
internal val NovaGreen = Color(0xFF078A63)
internal val NovaRed = Color(0xFFC93756)

internal fun novaDate(time: Long): String =
    SimpleDateFormat("MMM d • h:mm a", Locale.US).format(Date(time))


/** Three softly rounded blue layers from the original Nova Studio concept. */
@Composable
internal fun NovaLogo(modifier: Modifier = Modifier, size: Dp = 42.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val layers = listOf(
            Triple(.45f, Color(0xFF0752D8), Color(0xFF1D7FFF)),
            Triple(.25f, Color(0xFF176AEF), Color(0xFF50B7FF)),
            Triple(.05f, Color(0xFF3B8FFF), Color(0xFFB1E5FF))
        )
        layers.forEach { (dy, dark, light) ->
            val cy = h * dy
            val left = w * .10f
            val right = w * .90f
            val diamond = Path().apply {
                moveTo(w * .44f, cy)
                quadraticBezierTo(w * .50f, cy - h * .035f, w * .56f, cy)
                lineTo(right, cy + h * .13f)
                quadraticBezierTo(w * .96f, cy + h * .17f, right, cy + h * .22f)
                lineTo(w * .56f, cy + h * .37f)
                quadraticBezierTo(w * .50f, cy + h * .40f, w * .44f, cy + h * .37f)
                lineTo(left, cy + h * .22f)
                quadraticBezierTo(w * .04f, cy + h * .17f, left, cy + h * .13f)
                close()
            }
            drawPath(diamond, brush = Brush.linearGradient(
                colors = listOf(light, dark),
                start = Offset(w * .2f, cy),
                end = Offset(w * .8f, cy + h * .38f)))
            val base = Path().apply {
                moveTo(left, cy + h * .18f)
                quadraticBezierTo(w * .04f, cy + h * .22f, left, cy + h * .26f)
                lineTo(w * .44f, cy + h * .42f)
                quadraticBezierTo(w * .50f, cy + h * .45f, w * .56f, cy + h * .42f)
                lineTo(right, cy + h * .26f)
                quadraticBezierTo(w * .96f, cy + h * .22f, right, cy + h * .18f)
                lineTo(w * .56f, cy + h * .36f)
                quadraticBezierTo(w * .50f, cy + h * .40f, w * .44f, cy + h * .36f)
                close()
            }
            drawPath(base, color = dark.copy(alpha = .8f))
        }
    }
}

@Composable
internal fun NovaCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier, shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = .65f)),
        shadowElevation = 2.dp) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
internal fun NovaHint(value: String, modifier: Modifier = Modifier) {
    Text(value, modifier = modifier, color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.bodySmall)
}

@Composable
internal fun NovaHeader(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground)
        NovaHint(subtitle)
    }
}

@Composable
internal fun NovaSection(title: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        if (action != null && onAction != null)
            TextButton(onClick = onAction) { Text(action, color = NovaBlue) }
    }
}

@Composable
internal fun NovaPrimaryButton(text: String, onClick: () -> Unit,
                                enabled: Boolean = true, modifier: Modifier = Modifier) {
    Button(onClick = onClick, enabled = enabled,
        modifier = modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(17.dp),
        colors = ButtonDefaults.buttonColors(containerColor = NovaBlue, contentColor = Color.White)) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun NovaStatus(text: String, success: Boolean) {
    val tone = if (success) NovaGreen else Color(0xFFB17A20)
    Surface(shape = RoundedCornerShape(50), color = tone.copy(alpha = .10f)) {
        Text(text, color = tone, style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
    }
}

@Composable
internal fun NovaTile(icon: String, title: String, subtitle: String,
                      modifier: Modifier = Modifier, onClick: () -> Unit) {
    NovaCard(modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(color = NovaBlue.copy(alpha = .09f), shape = RoundedCornerShape(12.dp)) {
                Box(Modifier.size(37.dp), contentAlignment = Alignment.Center) {
                    Text(icon, color = NovaBlue, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                NovaHint(subtitle)
            }
            Text("›", color = NovaBlue)
        }
    }
}

@Composable
internal fun NovaBanner(project: Project, link: RepoLink?, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp), color = NovaBlue) {
        Box(Modifier.background(Brush.linearGradient(listOf(
            Color(0xFF428FFF), NovaBlue, NovaBlueDeep))).padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(14.dp), color = Color.White.copy(alpha = .18f)) {
                    NovaLogo(Modifier.padding(8.dp), 40.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("ACTIVE PROJECT", color = Color.White.copy(alpha = .78f),
                        style = MaterialTheme.typography.labelSmall, letterSpacing = 1.sp)
                    Text(project.name, fontWeight = FontWeight.ExtraBold, color = Color.White,
                        style = MaterialTheme.typography.titleLarge, maxLines = 1)
                    Text(if (link == null) "Local Android source" else "GitHub • " + link.branch,
                        color = Color.White.copy(alpha = .9f),
                        style = MaterialTheme.typography.bodySmall)
                }
                Text("›", color = Color.White, fontSize = 23.sp)
            }
        }
    }
}

@Composable
internal fun NovaTopBar(section: String, project: Project?, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (project == null) NovaLogo(size = 44.dp)
        else TextButton(onClick = onBack) { Text("‹", color = NovaBlue, fontSize = 26.sp) }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(project?.name ?: "BuildDeck", fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleLarge, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(if (project == null) "Nova Studio" else "PROJECT WORKSPACE",
                color = NovaBlue, letterSpacing = 1.sp,
                style = MaterialTheme.typography.labelSmall)
        }
        if (project == null && section != "Dashboard")
            Text(section.uppercase(Locale.US), style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun NovaBottomBar(section: String, onTab: (String) -> Unit) {
    NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        listOf("Dashboard" to "⌂", "Projects" to "▣", "Builds" to "ϟ",
            "Vault" to "◈", "Settings" to "⚙").forEach { (tab, glyph) ->
            NavigationBarItem(
                selected = section == tab, onClick = { onTab(tab) },
                icon = {
                    Text(glyph, fontSize = 20.sp, fontWeight = FontWeight.Bold,
                        color = if (tab == section) NovaBlue else MaterialTheme.colorScheme.onSurfaceVariant)
                },
                label = {
                    Text(if (tab == "Dashboard") "Home" else tab,
                        style = MaterialTheme.typography.labelSmall, maxLines = 1)
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedTextColor = NovaBlue, selectedIconColor = NovaBlue,
                    indicatorColor = NovaBlue.copy(alpha = .10f))
            )
        }
    }
}


/**
 * Native, scalable Nova Studio hero inspired by the original blue-wave artwork.
 * The developer/laptop scene is painted off to the right so headlines stay readable.
 */
@Composable
internal fun NovaHero() {
    val dark = MaterialTheme.colorScheme.background != NovaBg
    val ink = MaterialTheme.colorScheme.onBackground
    Box(
        Modifier.fillMaxWidth().height(178.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.horizontalGradient(
                    if (dark) listOf(Color(0xFF182942), Color(0xFF234C91), Color(0xFF1658CF))
                    else listOf(Color(0xFFFDFEFF), Color(0xFFE4F2FF), Color(0xFFB0D7FF))
                )
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val lowerWave = Path().apply {
                moveTo(0f, h * .75f)
                cubicTo(w * .22f, h * .53f, w * .37f, h * .95f, w * .58f, h * .65f)
                cubicTo(w * .72f, h * .45f, w * .84f, h * .53f, w, h * .31f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(lowerWave, Brush.verticalGradient(
                listOf(Color(0xFF80BEFF).copy(alpha=.21f), Color(0xFF2282FC).copy(alpha=.28f))))
            val waveHighlight = Path().apply {
                moveTo(0f, h * .91f)
                cubicTo(w * .28f, h * .70f, w * .37f, h * 1.06f, w * .62f, h * .77f)
                cubicTo(w * .79f, h * .55f, w * .85f, h * .70f, w, h * .57f)
            }
            drawPath(waveHighlight, color = Color.White.copy(alpha = .62f),
                style = Stroke(width = h * .012f, cap = StrokeCap.Round))

            drawCircle(Brush.radialGradient(
                listOf(Color(0xFF79E5FF).copy(alpha = .70f), Color.Transparent),
                center = Offset(w * .82f, h * .48f), radius = w * .33f),
                radius = w * .33f, center = Offset(w * .82f, h * .48f))

            // Floating glass code card.
            drawRoundRect(Color.White.copy(alpha = .18f),
                topLeft = Offset(w * .79f, h * .16f), size = Size(w * .175f, h * .33f),
                cornerRadius = CornerRadius(h * .06f))
            for (k in 0..3) {
                drawLine(Color(0xFFE3FCFF).copy(alpha = .65f),
                    Offset(w * .82f, h * (.23f + .06f * k)),
                    Offset(w * (.89f + .02f * (k % 2)), h * (.23f + .06f * k)),
                    strokeWidth = h * .011f, cap = StrokeCap.Round)
            }

            // Developer silhouette: shoulder, hoodie, head and cap.
            val hoodie = Path().apply {
                moveTo(w * .82f, h * .58f)
                quadraticBezierTo(w * .91f, h * .45f, w * .98f, h * .56f)
                lineTo(w * 1.07f, h * .98f)
                lineTo(w * .72f, h * .98f)
                quadraticBezierTo(w * .75f, h * .73f, w * .82f, h * .58f)
                close()
            }
            drawPath(hoodie, Brush.linearGradient(
                listOf(Color(0xFF0D3C92), Color(0xFF116AD5)),
                Offset(w * .76f, h * .58f), Offset(w, h)))
            drawCircle(brush = Brush.linearGradient(
                listOf(Color(0xFF2168B9), Color(0xFF082A65))),
                radius = h * .115f, center = Offset(w * .875f, h * .45f))
            val cap = Path().apply {
                moveTo(w * .795f, h * .40f)
                quadraticBezierTo(w * .84f, h * .27f, w * .91f, h * .32f)
                quadraticBezierTo(w * .94f, h * .36f, w * .96f, h * .42f)
                lineTo(w * .80f, h * .43f)
                close()
            }
            drawPath(cap, color = Color(0xFF0D377A))
            drawLine(Color(0xFF0B2E70),
                Offset(w * .78f, h * .43f), Offset(w * .94f, h * .45f),
                strokeWidth = h * .03f, cap = StrokeCap.Round)

            // The glowing screen/laptop sits in front of the developer.
            val laptop = Path().apply {
                moveTo(w * .62f, h * .54f)
                lineTo(w * .79f, h * .54f)
                lineTo(w * .83f, h * .84f)
                lineTo(w * .65f, h * .84f)
                close()
            }
            drawPath(laptop, Brush.linearGradient(
                listOf(Color(0xFF073C94), Color(0xFF207DFF)),
                Offset(w * .64f, h * .55f), Offset(w * .84f, h * .85f)))
            drawPath(laptop, Color(0xFF8DEBFF).copy(alpha = .92f),
                style = Stroke(width = h * .012f))
            drawLine(Color(0xFFB2F8FF), Offset(w * .71f, h * .65f),
                Offset(w * .69f, h * .69f), strokeWidth = h * .016f, cap = StrokeCap.Round)
            drawLine(Color(0xFFB2F8FF), Offset(w * .69f, h * .69f),
                Offset(w * .71f, h * .72f), strokeWidth = h * .016f, cap = StrokeCap.Round)
            drawLine(Color(0xFFB2F8FF), Offset(w * .75f, h * .65f),
                Offset(w * .77f, h * .69f), strokeWidth = h * .016f, cap = StrokeCap.Round)
            drawLine(Color(0xFFB2F8FF), Offset(w * .77f, h * .69f),
                Offset(w * .75f, h * .72f), strokeWidth = h * .016f, cap = StrokeCap.Round)
            drawLine(Color(0xFFB2F8FF), Offset(w * .735f, h * .64f),
                Offset(w * .725f, h * .74f), strokeWidth = h * .010f, cap = StrokeCap.Round)
            drawLine(Color(0xFF63B5FF), Offset(w * .61f, h * .86f),
                Offset(w * .88f, h * .86f), strokeWidth = h * .028f, cap = StrokeCap.Round)

            // Smaller floating layers echo the original BuildDeck logo.
            for (i in 2 downTo 0) {
                val y = h * (.16f + i * .085f)
                val tile = Path().apply {
                    moveTo(w * .72f, y)
                    lineTo(w * .79f, y + h * .04f)
                    lineTo(w * .72f, y + h * .085f)
                    lineTo(w * .65f, y + h * .04f)
                    close()
                }
                drawPath(tile, Brush.linearGradient(
                    listOf(Color(0xFFA0EFFF), Color(0xFF2389FE))))
            }
        }
        Column(Modifier.align(Alignment.CenterStart).fillMaxWidth(.62f)
            .padding(start = 16.dp, top = 2.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("YOUR MOBILE STUDIO", color = NovaBlue,
                style = MaterialTheme.typography.labelSmall, letterSpacing = 1.1.sp,
                fontWeight = FontWeight.Bold)
            Text("Build something\ngreat.", color = if (dark) Color.White else NovaInk,
                style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold,
                lineHeight = 29.sp)
            Text("Code  •  Build  •  Ship",
                color = if (dark) Color(0xFFDAECFF) else NovaMuted,
                style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
internal fun NovaDashboard(
    projects: List<Project>, releases: List<Release>,
    builds: List<BuildRecord>, activeLink: RepoLink?,
    inboxItems: List<AiSyncInboxEntry>, inboxChecking: Boolean,
    inboxLastCheckedAt: Long, linkedProjectCount: Int,
    onInboxRefresh: () -> Unit, onInboxReview: (String) -> Unit,
    onProject: (Project) -> Unit, onBuild: (Project) -> Unit,
    onProjects: () -> Unit, onBuilds: () -> Unit, onVault: () -> Unit,
    onGitHub: () -> Unit, onImport: () -> Unit,
    onViewLog: (BuildRecord) -> Unit, onImportApk: (Project) -> Unit,
    onCleaner: () -> Unit
) {
    var chosenId by remember(projects) { mutableStateOf(projects.firstOrNull()?.id) }
    var chooseProject by remember { mutableStateOf(false) }
    val active = projects.firstOrNull { it.id == chosenId } ?: projects.firstOrNull()
    val latest = builds.firstOrNull { it.status in setOf("SUBMITTED", "RUNNING") } ?: builds.firstOrNull()
    val context = LocalContext.current
    LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { NovaHero() }
        item {
            NovaCard {
                NovaHint("SELECTED PROJECT")
                Text(active?.let { SmartStudio.title(it.name) } ?: "No source selected", fontWeight = FontWeight.Bold)
                active?.let { NovaHint(SmartStudio.version(it.name)?.let { v -> "v$v" } ?: it.name) }
                OutlinedButton(onClick = { chooseProject = true }, enabled = projects.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Choose project / version") }
                NovaPrimaryButton("ϟ   Start build", onClick = { active?.let(onBuild) }, enabled = active != null)
            }
        }
        item { NovaPrimaryButton("+ Import source project ZIP", onImport) }
        if (latest == null) item { NovaCard { NovaHint("No builds yet. Tap Start build.") } }
        else item {
            BuildPulseCard(
                record = latest,
                stage = BuildTransferProvider.stage(context, latest.id),
                onViewLog = if (latest.logFile != null) { { onViewLog(latest) } } else null,
                onImportApk = projects.firstOrNull { it.id == latest.projectId }?.let { p -> { onImportApk(p) } },
                onOpenVault = onVault
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                NovaTile("▣", "Projects", "Manage source", Modifier.weight(1f), onProjects)
                NovaTile("◈", "APK Vault", releases.size.toString() + " saved", Modifier.weight(1f), onVault)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                NovaTile("ϟ", "Builds", "Build history", Modifier.weight(1f), onBuilds)
                NovaTile("◇", "Smart Cleaner", "Review storage", Modifier.weight(1f), onCleaner)
            }
        }
        item {
            NovaAiSyncInbox(
                entries = inboxItems, linkedProjects = linkedProjectCount,
                scanning = inboxChecking, checkedAt = inboxLastCheckedAt,
                onRefresh = onInboxRefresh, onReview = onInboxReview, onLinkProject = onProjects
            )
        }
        item { OutlinedButton(onClick = onGitHub, modifier = Modifier.fillMaxWidth()) { Text("GitHub source tools") } }
        if (releases.isNotEmpty()) item {
            NovaCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Latest APK", fontWeight = FontWeight.SemiBold)
                        NovaHint(releases.first().name)
                    }
                    TextButton(onClick = onVault) { Text("Open Vault ›", color = NovaBlue) }
                }
            }
        }
        item { NovaHint("Source updates and local builds still require your approval.") }
    }
    if (chooseProject) {
        var search by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { chooseProject = false }, title = { Text("Choose source version") },
            text = {
                Column {
                    OutlinedTextField(search, { search = it }, singleLine = true, placeholder = { Text("Search projects") })
                    LazyColumn(Modifier.heightIn(max = 340.dp)) {
                        items(projects.filter { it.name.contains(search, true) }, key = { it.id }) { project ->
                            TextButton(onClick = { chosenId = project.id; chooseProject = false }) {
                                Text(SmartStudio.title(project.name) + " " + (SmartStudio.version(project.name)?.let { "v$it" } ?: project.name), maxLines = 2)
                            }
                        }
                    }
                }
            }, confirmButton = { TextButton(onClick = { chooseProject = false }) { Text("Close") } })
    }
}

@Composable
internal fun NovaProjects(
    projects: List<Project>, releasesCount: Int, archivedCount: Int,
    githubAccount: String?, working: Boolean, onSelect: (Project) -> Unit,
    onImportZip: () -> Unit, onCleanup: () -> Unit, onRestore: () -> Unit,
    onGitHubConnect: (String) -> Unit, onGitHubDisconnect: () -> Unit,
    onGitHubImport: (String, String) -> Unit,
    groups: Map<String, List<Project>>
) {
    var search by remember { mutableStateOf("") }
    var showGitHub by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var favorites by remember { mutableStateOf(SmartStudio.favorites(context)) }
    var protected by remember { mutableStateOf(SmartStudio.protected(context)) }
    var expanded by remember { mutableStateOf(setOf<String>()) }
    val visible = groups.entries.filter { entry -> entry.value.any { it.name.contains(search, true) } }
        .sortedWith(compareByDescending<Map.Entry<String, List<Project>>> { it.key in favorites }
            .thenByDescending { it.value.maxOf { p -> p.created } })
    LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { NovaHeader("Projects", "One clean workspace for every Android app.") }
        item {
            OutlinedTextField(search, { search = it }, modifier = Modifier.fillMaxWidth(),
                singleLine = true, placeholder = { Text("⌕  Search projects") },
                shape = RoundedCornerShape(16.dp))
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                NovaCard(Modifier.weight(1f)) {
                    NovaHint("WORKSPACES • " + projects.size + " versions")
                    Text(groups.size.toString(), color = NovaBlue,
                        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                }
                NovaCard(Modifier.weight(1f)) {
                    NovaHint("ARCHIVED")
                    Text(archivedCount.toString(), color = NovaBlue,
                        style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
        item { NovaSection("Your workspaces") }
        if (visible.isEmpty()) item {
            NovaCard { NovaHint(if (projects.isEmpty()) "No projects yet. Import source to begin."
                else "No projects match your search.") }
        }
        items(visible, key = { it.key }) { entry ->
            val key = entry.key
            val versions = entry.value
            val newest = versions.first()
            NovaCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NovaLogo(size = 34.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f).clickable {
                        expanded = if (key in expanded) expanded - key else expanded + key
                    }) {
                        Text(SmartStudio.title(newest.name), fontWeight = FontWeight.Bold)
                        NovaHint(versions.size.toString() + " source versions • " +
                            (SmartStudio.version(newest.name)?.let { "v$it" } ?: novaDate(newest.created)))
                    }
                    TextButton(onClick = { favorites = SmartStudio.toggleFavorite(context, key) }) {
                        Text(if (key in favorites) "★" else "☆")
                    }
                    TextButton(onClick = { expanded = if (key in expanded) expanded - key else expanded + key }) {
                        Text(if (key in expanded) "−" else "+")
                    }
                }
                TextButton(onClick = { onSelect(newest) }) { Text("Open latest ›", color = NovaBlue) }
                if (key in expanded || search.isNotBlank()) {
                    versions.forEach { project ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f).clickable { onSelect(project) }) {
                                Text(SmartStudio.version(project.name)?.let { "v$it" } ?: project.name, fontWeight = FontWeight.SemiBold)
                                NovaHint(project.name + " • " + novaDate(project.created))
                            }
                            TextButton(onClick = { protected = SmartStudio.toggleProtected(context, project.id) }) {
                                Text(if (project.id in protected) "Protected" else "Protect")
                            }
                        }
                    }
                }
            }
        }
        item { NovaPrimaryButton("+ Import source project ZIP", onImportZip) }
        if (projects.size > 1) item {
            OutlinedButton(onClick = onCleanup, modifier = Modifier.fillMaxWidth()) {
                Text("Smart Cleaner • Review versions", color = NovaBlue)
            }
        }
        if (archivedCount > 0) item {
            TextButton(onClick = onRestore) {
                Text("Archived projects (" + archivedCount + ") • Restore", color = NovaBlue)
            }
        }
        item {
            NovaSection("GitHub workspace")
            NovaHint("Import repository source, review changes, or connect your GitHub token.")
            OutlinedButton(onClick = { showGitHub = !showGitHub },
                modifier = Modifier.fillMaxWidth()) {
                Text(if (showGitHub) "Hide GitHub tools" else "Open GitHub tools", color = NovaBlue)
            }
            if (showGitHub) {
                Spacer(Modifier.height(8.dp))
                GitHubImportCard(githubAccount, working,
                    onGitHubConnect, onGitHubDisconnect, onGitHubImport)
            }
        }
        item { NovaHint(releasesCount.toString() + " saved APKs stay in the Vault. Grouping never deletes sources.") }
    }
}
