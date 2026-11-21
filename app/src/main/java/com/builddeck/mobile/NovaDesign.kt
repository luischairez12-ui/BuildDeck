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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
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

@Composable
internal fun NovaLogo(modifier: Modifier = Modifier, size: Dp = 38.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val colors = listOf(Color(0xFFA4E0FF), Color(0xFF3F9BFF), NovaBlue)
        for (i in 2 downTo 0) {
            val top = h * (0.12f + i * 0.20f)
            val p = Path().apply {
                moveTo(w * .50f, top)
                lineTo(w * .91f, top + h * .16f)
                lineTo(w * .50f, top + h * .33f)
                lineTo(w * .09f, top + h * .16f)
                close()
            }
            drawPath(p, colors[i])
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
        if (project == null) NovaLogo(size = 36.dp)
        else TextButton(onClick = onBack) { Text("‹", color = NovaBlue, fontSize = 26.sp) }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text(project?.name ?: "BuildDeck", fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleLarge, maxLines = 1,
                overflow = TextOverflow.Ellipsis)
            Text(if (project == null) "NOVA STUDIO" else "PROJECT WORKSPACE",
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

@Composable
internal fun NovaDashboard(
    projects: List<Project>, releases: List<Release>,
    builds: List<BuildRecord>, activeLink: RepoLink?,
    onProject: (Project) -> Unit, onBuild: (Project) -> Unit,
    onProjects: () -> Unit, onBuilds: () -> Unit, onVault: () -> Unit,
    onGitHub: () -> Unit, onImport: () -> Unit
) {
    val active = projects.firstOrNull()
    val latest = builds.firstOrNull()
    LazyColumn(contentPadding = PaddingValues(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item { NovaHeader("Your app studio.", "Build, sync, and ship Android apps from one place.") }
        item {
            if (active != null) NovaBanner(active, activeLink) { onProject(active) }
            else NovaCard {
                Text("Start a new project", fontWeight = FontWeight.Bold)
                NovaHint("Import a complete Android Gradle source project.")
                NovaPrimaryButton("+ Import project", onImport)
            }
        }
        item { NovaPrimaryButton("ϟ   Build APK on this phone",
            onClick = { if (active != null) onBuild(active) },
            enabled = active != null) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                NovaTile("▣", "Projects", "Manage source", Modifier.weight(1f), onProjects)
                NovaTile("⇅", "GitHub", "Sync & review", Modifier.weight(1f), onGitHub)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                NovaTile("◈", "APK Vault", releases.size.toString() + " saved",
                    Modifier.weight(1f), onVault)
                NovaTile("ϟ", "Builds", "Build history", Modifier.weight(1f), onBuilds)
            }
        }
        item { NovaSection("Recent activity", "View builds", onBuilds) }
        item {
            NovaCard {
                if (latest == null) NovaHint("No build requests yet. Start with the blue Build APK button.")
                else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Build " + latest.id.take(8), fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f))
                        NovaStatus(latest.status.replace('_', ' '), latest.status == "READY")
                    }
                    NovaHint(novaDate(latest.submittedAt))
                    if (latest.note.isNotBlank()) NovaHint(latest.note.take(170))
                }
                if (releases.isNotEmpty()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("Latest APK", fontWeight = FontWeight.SemiBold)
                    NovaHint(releases.first().name)
                    TextButton(onClick = onVault) { Text("Open Vault ›", color = NovaBlue) }
                }
            }
        }
        item { NovaHint("Source updates and local builds still require your approval.") }
    }
}

@Composable
internal fun NovaProjects(
    projects: List<Project>, releasesCount: Int, archivedCount: Int,
    githubAccount: String?, working: Boolean, onSelect: (Project) -> Unit,
    onImportZip: () -> Unit, onCleanup: () -> Unit, onRestore: () -> Unit,
    onGitHubConnect: (String) -> Unit, onGitHubDisconnect: () -> Unit,
    onGitHubImport: (String, String) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var showGitHub by remember { mutableStateOf(false) }
    val visible = projects.filter { it.name.contains(search, ignoreCase = true) }
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
                    NovaHint("ACTIVE PROJECTS")
                    Text(projects.size.toString(), color = NovaBlue,
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
        items(visible, key = { it.id }) { project ->
            NovaCard(Modifier.fillMaxWidth().clickable { onSelect(project) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NovaLogo(size = 34.dp)
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Text(project.name, fontWeight = FontWeight.Bold)
                        NovaHint("Android project • " + novaDate(project.created))
                    }
                    Text("›", color = NovaBlue, fontSize = 24.sp)
                }
            }
        }
        item { NovaPrimaryButton("+ Import source project ZIP", onImportZip) }
        if (projects.size > 1) item {
            OutlinedButton(onClick = onCleanup, modifier = Modifier.fillMaxWidth()) {
                Text("Clean up duplicates • Keep one", color = NovaBlue)
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
        item { NovaHint(releasesCount.toString() + " saved APKs stay in the separate Vault.") }
    }
}
