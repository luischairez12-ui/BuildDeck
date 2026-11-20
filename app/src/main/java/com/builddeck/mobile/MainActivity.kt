package com.builddeck.mobile

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Night = Color(0xFF0B111D)
private val Panel = Color(0xFF151F31)
private val Accent = Color(0xFF78D9F8)
private val TextSoft = Color(0xFFB1C0D3)

class MainActivity : ComponentActivity() {
    // A file manager can share an APK with BuildDeck even if Android's
    // Downloads document picker refuses to enumerate Termux-created files.
    private var incomingApk by mutableStateOf<Uri?>(null)

    private fun sharedApk(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_SEND) return null
        return intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
            ?: intent.clipData?.getItemAt(0)?.uri
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incomingApk = sharedApk(intent)
        val store = Store(this)
        setContent {
            BuildDeckApp(store, incomingApk) { incomingApk = null }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingApk = sharedApk(intent)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuildDeckApp(store: Store, incomingSharedApk: Uri?, onShareHandled: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var projects by remember { mutableStateOf(store.projects()) }
    var releases by remember { mutableStateOf(store.releases()) }
    var section by remember { mutableStateOf("Projects") }
    var selectedId by remember { mutableStateOf<String?>(null) }
    var editorPath by remember { mutableStateOf<String?>(null) }
    var editorText by remember { mutableStateOf("") }
    var pendingBuild by remember { mutableStateOf<Project?>(null) }
    var buildConfirm by remember { mutableStateOf<Project?>(null) }
    var restoreConfirm by remember { mutableStateOf<Pair<Project, Snapshot>?>(null) }
    var exportProject by remember { mutableStateOf<Project?>(null) }
    var importApkFor by remember { mutableStateOf<Project?>(null) }
    var busy by remember { mutableStateOf(false) }
    val aiControl = remember(store) { AiControlStore(context, store) }
    val outputCollector = remember(context) { OutputCollector(context) }
    val githubWorkspace = remember(context, store) { GitHubWorkspace(context, store) }
    val incomingBridge = remember(store, githubWorkspace) { GitHubIncoming(store, githubWorkspace) }
    var outputConnected by remember { mutableStateOf(outputCollector.isConnected()) }
    var githubAccount by remember { mutableStateOf(githubWorkspace.connectedAccount()) }
    var githubRevision by remember { mutableIntStateOf(0) }
    var syncProject by remember { mutableStateOf<Project?>(null) }
    var syncCandidates by remember { mutableStateOf<List<SyncCandidate>>(emptyList()) }
    var syncSelected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var incomingProject by remember { mutableStateOf<Project?>(null) }
    var incomingReview by remember { mutableStateOf<IncomingReview?>(null) }
    var incomingSelected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var incomingBuildAfter by remember { mutableStateOf(true) }
    var accessRevision by remember { mutableIntStateOf(0) }
    var patchForProject by remember { mutableStateOf<Project?>(null) }
    var patchReview by remember { mutableStateOf<PatchBundle?>(null) }
    var patchPreviews by remember { mutableStateOf<List<PatchPreview>>(emptyList()) }
    var patchSelected by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var logForBuild by remember { mutableStateOf<BuildRecord?>(null) }
    var visibleLog by remember { mutableStateOf<String?>(null) }
    var outputScanResult by remember { mutableStateOf<String?>(null) }
    var buildRevision by remember { mutableIntStateOf(0) }

    fun notify(message: String) { Toast.makeText(context, message, Toast.LENGTH_LONG).show() }
    fun reload() { projects = store.projects(); releases = store.releases() }

    // A Termux callback can arrive while BuildDeck is in the background.
    // Refresh local build status/release cards on returning to the app.
    DisposableEffect(context) {
        val owner = context as? ComponentActivity
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                buildRevision++
                reload()
                outputConnected = outputCollector.isConnected()
            }
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

    // While a project is open, read granted build logs for live stages and APKs.
    LaunchedEffect(selectedId, outputConnected) {
        if (selectedId != null && outputConnected) {
            while (true) {
                val updated = withContext(Dispatchers.IO) {
                    runCatching { outputCollector.reconcile(store, aiControl) }.getOrDefault(false)
                }
                if (updated) { buildRevision++; reload() }
                delay(4000)
            }
        }
    }

    fun executeLocalBuild(project: Project) {
        scope.launch {
            busy = true
            try {
                val handoff = withContext(Dispatchers.IO) { store.handoffForLocalBuild(project) }
                val record = aiControl.recordBuild(project)
                try {
                    LocalBuildBridge.start(context, handoff, record.id)
                    notify("Build started in Termux. The result will appear in this project's Build Requests.")
                } catch (error: Exception) {
                    aiControl.completeLocalBuild(record.id, "FAILED", "Couldn't start Termux: ${error.message}", error.stackTraceToString())
                    throw error
                }
                buildRevision++
            } catch (ex: Exception) {
                notify("Build couldn't start: ${ex.message}")
            } finally { busy = false }
        }
    }

    val termuxPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val project = pendingBuild
        pendingBuild = null
        if (granted && project != null) executeLocalBuild(project)
        else notify("Grant Termux command permission in BuildDeck's app settings to enable local builds.")
    }
    val outputPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            try {
                outputCollector.connect(uri)
                outputConnected = true
                scope.launch {
                    val changed = withContext(Dispatchers.IO) {
                        runCatching { outputCollector.reconcile(store, aiControl) }.getOrDefault(false)
                    }
                    if (changed) { buildRevision++; reload() }
                }
                notify("Output folder linked. Completed APKs can now import automatically.")
            } catch (error: Exception) {
                notify("Choose Downloads / BuildDeck / Out: ${error.message}")
            }
        }
    }
    val zipPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            try {
                val project = withContext(Dispatchers.IO) { store.importProject(uri) }
                reload(); section = "Projects"; selectedId = project.id
                notify("Imported ${project.name}")
            } catch (ex: Exception) { notify("Import failed: ${ex.message}") }
            finally { busy = false }
        }
    }
    val apkPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val project = importApkFor
        importApkFor = null
        if (uri != null && project != null) scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) { store.importRelease(project, uri) }
                reload(); notify("APK added to your release library")
            } catch (ex: Exception) { notify("APK import failed: ${ex.message}") }
            finally { busy = false }
        }
    }
    val patchPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val project = patchForProject
        patchForProject = null
        if (uri != null && project != null) scope.launch {
            busy = true
            try {
                val (bundle, previews) = withContext(Dispatchers.IO) {
                    val bundle = aiControl.readPatch(uri, project)
                    bundle to aiControl.preview(project, bundle)
                }
                patchReview = bundle
                patchPreviews = previews
                patchSelected = previews.filter { it.conflict == null }.map { it.index }.toSet()
            } catch (ex: Exception) { notify("Cannot review AI patch: ${ex.message}") }
            finally { busy = false }
        }
    }
    val logPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val record = logForBuild
        logForBuild = null
        if (uri != null && record != null) scope.launch {
            busy = true
            try {
                val updated = withContext(Dispatchers.IO) { aiControl.importBuildLog(record.id, uri) }
                buildRevision++
                notify("Log imported. Status: ${updated.status}. Only a success marker confirms completion.")
            } catch (ex: Exception) { notify("Log import failed: ${ex.message}") }
            finally { busy = false }
        }
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        val project = exportProject
        exportProject = null
        if (uri != null && project != null) scope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) { store.exportSource(project, uri) }
                notify("Project exported as a ZIP")
            } catch (ex: Exception) { notify("Export failed: ${ex.message}") }
            finally { busy = false }
        }
    }

    fun requestBuild(project: Project) {
        if (ContextCompat.checkSelfPermission(context, "com.termux.permission.RUN_COMMAND") == PackageManager.PERMISSION_GRANTED) {
            executeLocalBuild(project)
        } else {
            pendingBuild = project
            termuxPermission.launch("com.termux.permission.RUN_COMMAND")
        }
    }

    fun installRelease(release: Release) {
        try {
            val metadata = ApkInspector.inspect(context, release.path)
            if (metadata?.warning == true) {
                notify(metadata.verdict + ". The installed app is unchanged.")
                return
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", File(release.path))
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (ex: Exception) { notify("Unable to open APK: ${ex.message}") }
    }

    val colors = darkColorScheme(
        primary = Accent,
        onPrimary = Night,
        background = Night,
        surface = Panel,
        surfaceVariant = Color(0xFF1E2A3E),
        onSurface = Color.White,
        onBackground = Color.White,
        secondary = Color(0xFFA7A5FF)
    )

    MaterialTheme(colorScheme = colors) {
        Scaffold(
            containerColor = Night,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("BUILDDECK", fontWeight = FontWeight.ExtraBold)
                            Text("PRIVATE DEVELOPMENT STUDIO", style = MaterialTheme.typography.labelSmall, color = TextSoft)
                        }
                    },
                    navigationIcon = {
                        if (editorPath != null || selectedId != null) {
                            TextButton(onClick = {
                                if (editorPath != null) editorPath = null else selectedId = null
                            }) { Text("‹ Back") }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Night)
                )
            },
            bottomBar = {
                if (editorPath == null && selectedId == null) {
                    NavigationBar(containerColor = Panel) {
                        listOf("Projects", "Releases", "Settings").forEachIndexed { index, tab ->
                            NavigationBarItem(
                                selected = section == tab,
                                onClick = { section = tab },
                                icon = { Text(listOf("▦", "⬡", "⚙")[index]) },
                                label = { Text(tab) }
                            )
                        }
                    }
                }
            }
        ) { inner ->
            Box(Modifier.padding(inner).fillMaxSize()) {
                val project = projects.firstOrNull { it.id == selectedId }
                when {
                    project != null && editorPath != null -> {
                        SourceEditor(
                            path = editorPath!!,
                            content = editorText,
                            onChange = { editorText = it },
                            onSave = {
                                try { store.saveText(project, editorPath!!, editorText); notify("File saved") }
                                catch (ex: Exception) { notify("Save failed: ${ex.message}") }
                            }
                        )
                    }
                    project != null -> {
                        ProjectDetail(
                            project = project,
                            filePaths = remember(project.id, projects, githubRevision) { store.sourceFiles(project) },
                            releases = releases.filter { it.projectId == project.id },
                            snapshots = store.snapshots(project),
                            access = run { accessRevision; aiControl.access(project) },
                            buildRecords = run { buildRevision; aiControl.buildRecords(project.id) },
                            onAccessChanged = { policy ->
                                try { aiControl.setAccess(project, policy); accessRevision++ }
                                catch (ex: Exception) { notify("Couldn't save permissions: ${ex.message}") }
                            },
                            onImportPatch = {
                                patchForProject = project
                                patchPicker.launch(arrayOf("application/json", "text/plain", "*/*"))
                            },
                            onLogImport = { record ->
                                logForBuild = record
                                logPicker.launch(arrayOf("text/plain", "application/octet-stream", "*/*"))
                            },
                            onViewLog = { record ->
                                try { visibleLog = aiControl.buildLog(record) }
                                catch (ex: Exception) { notify("Cannot read log: ${ex.message}") }
                            },
                            onRestore = { snapshot -> restoreConfirm = project to snapshot },
                            onEdit = { path ->
                                try { editorText = store.readText(project, path); editorPath = path }
                                catch (ex: Exception) { notify("Cannot open file: ${ex.message}") }
                            },
                            onBuild = { buildConfirm = project },
                            sourceReady = store.isGradleProject(project),
                            onOpenGitHubImport = {
                                selectedId = null
                                editorPath = null
                                section = "Projects"
                            },
                            outputConnected = outputConnected,
                            onConnectOutput = { outputPicker.launch(null) },
                            onScanOutput = {
                                scope.launch {
                                    busy = true
                                    try {
                                        val result = withContext(Dispatchers.IO) {
                                            outputCollector.scanSummary(store, aiControl)
                                        }
                                        buildRevision++
                                        reload()
                                        outputScanResult = result
                                    } catch (error: Exception) {
                                        outputScanResult = "Output scan failed: " + error.message.orEmpty()
                                    } finally { busy = false }
                                }
                            },
                            repoLink = run { githubRevision; githubWorkspace.link(project) },
                            onFetchGitHub = {
                                scope.launch {
                                    busy = true
                                    try {
                                        val review = withContext(Dispatchers.IO) { incomingBridge.check(project) }
                                        incomingProject = project
                                        incomingReview = review
                                        incomingSelected = review.changes
                                            .filter { it.conflict == null && it.text != null }
                                            .map { it.path }.toSet()
                                        incomingBuildAfter = true
                                    } catch (error: Exception) {
                                        notify("GitHub fetch failed: ${error.message}")
                                    } finally { busy = false }
                                }
                            },
                            onReviewSync = {
                                scope.launch {
                                    busy = true
                                    try {
                                        val candidates = withContext(Dispatchers.IO) { githubWorkspace.changedFiles(project) }
                                        syncProject = project
                                        syncCandidates = candidates
                                        syncSelected = candidates.map { it.path }.toSet()
                                    } catch (error: Exception) { notify("Unable to review GitHub changes: ${error.message}") }
                                    finally { busy = false }
                                }
                            },
                            onDismissBuild = { record ->
                                aiControl.dismissBuild(record.id)
                                buildRevision++
                            },
                            onSaveSnapshot = {
                                scope.launch {
                                    busy = true
                                    try { withContext(Dispatchers.IO) { store.saveSnapshot(project) }; reload(); notify("Private source snapshot saved") }
                                    catch (ex: Exception) { notify("Snapshot failed: ${ex.message}") }
                                    finally { busy = false }
                                }
                            },
                            onExport = { exportProject = project; exportPicker.launch("${project.name}.zip") },
                            onImportApk = { importApkFor = project; apkPicker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream", "*/*")) },
                            onInstall = ::installRelease
                        )
                    }
                    section == "Projects" -> ProjectHome(projects, releases.size,
                        githubAccount = githubAccount, githubBusy = busy,
                        onGitHubConnect = { token ->
                            scope.launch {
                                busy = true
                                try {
                                    val account = withContext(Dispatchers.IO) { githubWorkspace.connectAccount(token) }
                                    githubAccount = account
                                    notify("Connected GitHub @$account")
                                } catch (error: Exception) { notify("GitHub connect failed: ${error.message}") }
                                finally { busy = false }
                            }
                        },
                        onGitHubDisconnect = {
                            githubWorkspace.disconnectAccount()
                            githubAccount = null
                            notify("GitHub token disconnected")
                        },
                        onGitHubImport = { spec, branch ->
                            scope.launch {
                                busy = true
                                try {
                                    val project = withContext(Dispatchers.IO) { githubWorkspace.importRepo(spec, branch) }
                                    reload(); githubRevision++
                                    selectedId = project.id
                                    notify("Imported ${project.name} from GitHub")
                                } catch (error: Exception) { notify("GitHub import failed: ${error.message}") }
                                finally { busy = false }
                            }
                        },
                        onImport = { zipPicker.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream", "*/*")) },
                        onSelect = { selectedId = it.id }
                    )
                    section == "Releases" -> ReleaseHome(releases, projects, onSelect = { selectedId = it }, onInstall = ::installRelease)
                    else -> SettingsPage(outputConnected,
                        onConnect = { outputPicker.launch(null) },
                        onDisconnect = {
                            outputCollector.disconnect()
                            outputConnected = false
                        },
                        githubAccount = githubAccount)
                }
                if (busy) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
                }
            }
        }

        val currentIncoming = incomingReview
        val incomingTarget = incomingProject
        if (currentIncoming != null && incomingTarget != null) {
            IncomingGithubReview(
                review = currentIncoming,
                selected = incomingSelected,
                onSelected = { incomingSelected = it },
                buildAfter = incomingBuildAfter,
                onBuildAfterChanged = { incomingBuildAfter = it },
                onDismiss = { incomingReview = null; incomingProject = null },
                onApply = {
                    val approved = incomingSelected
                    val wantsBuild = incomingBuildAfter
                    incomingReview = null
                    incomingProject = null
                    scope.launch {
                        busy = true
                        try {
                            val safety = withContext(Dispatchers.IO) {
                                incomingBridge.apply(incomingTarget, currentIncoming, approved)
                            }
                            githubRevision++
                            reload()
                            notify("Applied ${approved.size} GitHub files. Backup: ${safety.name}")
                            if (wantsBuild) buildConfirm = incomingTarget
                        } catch (error: Exception) {
                            notify("Incoming changes not applied: ${error.message}")
                        } finally { busy = false }
                    }
                }
            )
        }

        if (syncProject != null) {
            val target = syncProject!!
            GitHubSyncReview(
                projectName = target.name,
                files = syncCandidates,
                selected = syncSelected,
                onSelected = { syncSelected = it },
                onDismiss = { syncProject = null },
                onCommit = {
                    val paths = syncSelected.toList()
                    syncProject = null
                    scope.launch {
                        busy = true
                        try {
                            val count = withContext(Dispatchers.IO) { githubWorkspace.pushChanges(target, paths) }
                            githubRevision++
                            notify("Committed $count approved source files to GitHub")
                        } catch (error: Exception) {
                            notify("GitHub sync stopped: ${error.message}. Earlier files may have committed.")
                        } finally { busy = false }
                    }
                }
            )
        }
        if (patchReview != null) {
            val bundle = patchReview!!
            val selectedProject = projects.firstOrNull { it.id == selectedId }
            if (selectedProject != null) PatchReviewDialog(
                bundle = bundle,
                previews = patchPreviews,
                selected = patchSelected,
                onSelected = { patchSelected = it },
                onDismiss = { patchReview = null },
                onApply = {
                    patchReview = null
                    scope.launch {
                        busy = true
                        try {
                            val snapshot = withContext(Dispatchers.IO) {
                                aiControl.applyPatch(selectedProject, bundle, patchSelected)
                            }
                            reload()
                            notify("Patch applied; rollback snapshot: ${snapshot.name}")
                            if (bundle.requestBuild && aiControl.access(selectedProject).build) {
                                buildConfirm = selectedProject
                            } else if (bundle.requestBuild) {
                                notify("Build request held: enable AI build permission to allow requesting a build.")
                            }
                        } catch (ex: Exception) { notify("Patch failed: ${ex.message}") }
                        finally { busy = false }
                    }
                }
            )
        }
        if (incomingSharedApk != null) AlertDialog(
            onDismissRequest = onShareHandled,
            title = { Text("Save shared APK to Vault") },
            text = {
                Column(
                    Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("Choose the project for this APK. BuildDeck copies it into the private Vault without installing it.",
                        style = MaterialTheme.typography.bodySmall)
                    if (projects.isEmpty()) Text("Import a source project first.")
                    projects.forEach { target ->
                        OutlinedButton(onClick = {
                            val apk = incomingSharedApk
                            onShareHandled()
                            scope.launch {
                                busy = true
                                try {
                                    withContext(Dispatchers.IO) { store.importRelease(target, apk) }
                                    reload()
                                    selectedId = null
                                    section = "Releases"
                                    notify("APK saved under " + target.name)
                                } catch (error: Exception) {
                                    notify("Could not import shared APK: " + error.message)
                                } finally { busy = false }
                            }
                        }, modifier = Modifier.fillMaxWidth()) {
                            Text(target.name, maxLines = 2)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onShareHandled) { Text("Cancel") } }
        )

        if (outputScanResult != null) AlertDialog(
            onDismissRequest = { outputScanResult = null },
            title = { Text("Build Output Scan") },
            text = { Text(outputScanResult.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { outputScanResult = null }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { outputScanResult = null; outputPicker.launch(null) }) {
                    Text("Reconnect Out folder")
                }
            }
        )

        if (visibleLog != null) AlertDialog(
            onDismissRequest = { visibleLog = null },
            title = { Text("Build log") },
            text = {
                Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                    Text(visibleLog ?: "", style = MaterialTheme.typography.labelSmall)
                }
            },
            confirmButton = { TextButton(onClick = { visibleLog = null }) { Text("Close") } },
            dismissButton = {
                TextButton(onClick = {
                    val text = visibleLog.orEmpty().takeLast(16000)
                    try {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, text)
                        }
                        context.startActivity(Intent.createChooser(share, "Share build diagnostics"))
                    } catch (error: Exception) {
                        notify("Cannot share diagnostics: ${error.message}")
                    }
                }) { Text("Share log") }
            }
        )

        if (restoreConfirm != null) AlertDialog(
            onDismissRequest = { restoreConfirm = null },
            title = { Text("Restore this source snapshot?") },
            text = { Text("This will replace the current project source files. BuildDeck saves a safety snapshot of your current files before restoring.") },
            confirmButton = {
                Button(onClick = {
                    val (p, snap) = restoreConfirm!!
                    restoreConfirm = null
                    scope.launch {
                        busy = true
                        try { withContext(Dispatchers.IO) { store.restoreSnapshot(p, snap) }; reload(); notify("Source snapshot restored") }
                        catch (ex: Exception) { notify("Restore failed: ${ex.message}") }
                        finally { busy = false }
                    }
                }) { Text("Restore version") }
            },
            dismissButton = { TextButton(onClick = { restoreConfirm = null }) { Text("Cancel") } }
        )

        if (buildConfirm != null) AlertDialog(
            onDismissRequest = { buildConfirm = null },
            title = { Text("Run this project's build?") },
            text = { Text("BuildDeck will send your approved source to Termux, compile with your on-phone SDK, and report success or errors here. Gradle scripts can execute code. Only build trusted projects.") },
            confirmButton = {
                Button(onClick = {
                    val selected = buildConfirm!!
                    buildConfirm = null
                    requestBuild(selected)
                }) { Text("Build on phone") }
            },
            dismissButton = { TextButton(onClick = { buildConfirm = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun ProjectHome(
    projects: List<Project>, releases: Int, onImport: () -> Unit, onSelect: (Project) -> Unit,
    githubAccount: String?, githubBusy: Boolean,
    onGitHubConnect: (String) -> Unit, onGitHubDisconnect: () -> Unit,
    onGitHubImport: (String, String) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Your app universe.", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Every project, every version, all in one private place.", color = TextSoft)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Metric("PROJECTS", "${projects.size}", Modifier.weight(1f))
                Metric("APK LIBRARY", "$releases", Modifier.weight(1f))
            }
            Spacer(Modifier.height(20.dp))
            Button(onClick = onImport, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("+  Import source project ZIP") }
            Text("Your sources stay on this device unless you explicitly export them.", style = MaterialTheme.typography.bodySmall, color = TextSoft, modifier = Modifier.padding(top = 10.dp))
            Spacer(Modifier.height(18.dp))
            Text("MY PROJECTS", style = MaterialTheme.typography.labelLarge, color = Accent)
        }
        item {
            GitHubImportCard(githubAccount, githubBusy, onGitHubConnect, onGitHubDisconnect, onGitHubImport)
        }
        if (projects.isEmpty()) {
            item { ContentPanel { Text("No projects yet. Import an Android Gradle project ZIP to get started.", color = TextSoft) } }
        } else {
            items(projects, key = { it.id }) { project ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onSelect(project) },
                    colors = CardDefaults.cardColors(containerColor = Panel),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(14.dp), color = Color(0xFF203E4D)) {
                            Text("⌘", style = MaterialTheme.typography.headlineMedium, color = Accent, modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(project.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Local project  •  ${formatDate(project.created)}", color = TextSoft, style = MaterialTheme.typography.bodySmall)
                        }
                        Text("›", style = MaterialTheme.typography.headlineSmall, color = Accent)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun Metric(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextSoft)
            Text(value, style = MaterialTheme.typography.headlineLarge, color = Accent, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ProjectDetail(
    project: Project, filePaths: List<String>, releases: List<Release>, snapshots: List<Snapshot>,
    access: AiAccess, buildRecords: List<BuildRecord>,
    onAccessChanged: (AiAccess) -> Unit,
    onImportPatch: () -> Unit,
    onLogImport: (BuildRecord) -> Unit,
    onViewLog: (BuildRecord) -> Unit,
    onRestore: (Snapshot) -> Unit,
    onEdit: (String) -> Unit, onBuild: () -> Unit, onSaveSnapshot: () -> Unit,
    sourceReady: Boolean, onOpenGitHubImport: () -> Unit,
    outputConnected: Boolean, onConnectOutput: () -> Unit, onScanOutput: () -> Unit,
    repoLink: RepoLink?, onReviewSync: () -> Unit, onFetchGitHub: () -> Unit,
    onDismissBuild: (BuildRecord) -> Unit,
    onExport: () -> Unit, onImportApk: () -> Unit, onInstall: (Release) -> Unit
) {
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(project.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("SOURCE WORKSPACE", style = MaterialTheme.typography.labelMedium, color = Accent)
            Spacer(Modifier.height(14.dp))
            Button(onClick = onBuild, enabled = sourceReady,
                modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("⚡  BUILD APK ON THIS PHONE") }
            if (sourceReady) {
                Text("Source verified: Gradle settings file found. One-tap build uses Gradle 8.9 + SDK 35 + ARM AAPT2.",
                    color = TextSoft, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp))
            } else {
                Text("SOURCE IMPORT NEEDS REPAIR", color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                Text("This project is missing settings.gradle or settings.gradle.kts. It may contain a compiled APK instead of the original Android source. Import the actual repository rather than rebuilding this archive.",
                    color = TextSoft, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenGitHubImport, modifier = Modifier.fillMaxWidth()) {
                    Text("Go to GitHub source import")
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onSaveSnapshot, modifier = Modifier.weight(1f)) { Text("Save version") }
                OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) { Text("Export ZIP") }
            }
            Spacer(Modifier.height(16.dp))
            Text("SOURCE FILES (${filePaths.size})", style = MaterialTheme.typography.labelMedium, color = Accent)
            Text("Tap a text file to edit it. Saving changes updates your next build.", color = TextSoft, style = MaterialTheme.typography.bodySmall)
        }
        item {
            if (!outputConnected) {
                ContentPanel {
                    Text("AUTO APK COLLECTION", color = Accent, fontWeight = FontWeight.Bold)
                    Text("Give BuildDeck read-only access to Downloads / BuildDeck / Out, so finished APKs and logs can be collected automatically.", color = TextSoft)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onConnectOutput, modifier = Modifier.fillMaxWidth()) {
                        Text("Connect Build Output Folder")
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
            if (outputConnected) {
                OutlinedButton(onClick = onScanOutput, modifier = Modifier.fillMaxWidth()) {
                    Text("Scan Out folder and collect APKs")
                }
                Spacer(Modifier.height(12.dp))
            }
            OutlinedButton(onClick = onImportApk, modifier = Modifier.fillMaxWidth()) {
                Text("Choose a finished APK manually")
            }
            Spacer(Modifier.height(12.dp))
            GitHubProjectCard(repoLink, false, onReviewSync, onFetchGitHub)
            Spacer(Modifier.height(12.dp))
            AiProjectControls(access, onAccessChanged, onImportPatch)
            Spacer(Modifier.height(12.dp))
            BuildRequestHistory(buildRecords, onLogImport, onViewLog, onDismissBuild)
            Spacer(Modifier.height(12.dp))
        }
        if (filePaths.isEmpty()) item { ContentPanel { Text("No editable text files found.", color = TextSoft) } }
        items(filePaths.take(150), key = { it }) { path ->
            ContentPanel {
                Row(Modifier.fillMaxWidth().clickable { onEdit(path) }, verticalAlignment = Alignment.CenterVertically) {
                    Text("⌁", color = Accent)
                    Spacer(Modifier.width(10.dp))
                    Text(path, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("Edit ›", color = Accent, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Text("SAVED VERSIONS (${snapshots.size})", style = MaterialTheme.typography.labelMedium, color = Accent)
        }
        items(snapshots.take(20), key = { it.fileName }) { snapshot ->
            ContentPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Source snapshot", fontWeight = FontWeight.Medium)
                        Text(formatDate(snapshot.created), style = MaterialTheme.typography.bodySmall, color = TextSoft)
                    }
                    TextButton(onClick = { onRestore(snapshot) }) { Text("Restore") }
                }
            }
        }
        item {
            Text("RELEASES (${releases.size})", style = MaterialTheme.typography.labelMedium, color = Accent)
            OutlinedButton(onClick = onImportApk, modifier = Modifier.fillMaxWidth()) { Text("+ Import a built APK") }
        }
        items(releases, key = { it.id }) { release ->
            val appContext = LocalContext.current
            val details = remember(release.path) { ApkInspector.inspect(appContext, release.path) }
            ContentPanel {
                if (details != null) Text(details.verdict, color = if (details.warning) MaterialTheme.colorScheme.error else Accent,
                    style = MaterialTheme.typography.bodySmall)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(release.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatDate(release.added), color = TextSoft, style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { onInstall(release) }) { Text("Install") }
                }
            }
        }
    }
}

@Composable
private fun SourceEditor(path: String, content: String, onChange: (String) -> Unit, onSave: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("CODE EDITOR", color = Accent, style = MaterialTheme.typography.labelMedium)
        Text(path, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = content,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth().weight(1f),
            textStyle = MaterialTheme.typography.bodySmall,
            label = { Text("Source") },
            singleLine = false
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("Save file") }
    }
}

@Composable
private fun ReleaseHome(releases: List<Release>, projects: List<Project>, onSelect: (String) -> Unit, onInstall: (Release) -> Unit) {
    LazyColumn(contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("APK Vault", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Your private installable release library.", color = TextSoft)
            Spacer(Modifier.height(10.dp))
        }
        if (releases.isEmpty()) item { ContentPanel { Text("No APKs saved. Open a project to import a finished APK.", color = TextSoft) } }
        items(releases, key = { it.id }) { release ->
            val appContext = LocalContext.current
            val details = remember(release.path) { ApkInspector.inspect(appContext, release.path) }
            ContentPanel {
                Text(release.name, fontWeight = FontWeight.SemiBold)
                if (details != null) {
                    Text("${details.packageId} • v${details.version} (${details.versionCode})", color = TextSoft,
                        style = MaterialTheme.typography.bodySmall)
                    Text(details.verdict, color = if (details.warning) MaterialTheme.colorScheme.error else Accent,
                        style = MaterialTheme.typography.bodySmall)
                }
                Text(projects.find { it.id == release.projectId }?.name ?: "Unknown project", color = TextSoft, style = MaterialTheme.typography.bodySmall)
                Row {
                    TextButton(onClick = { onInstall(release) }) { Text("Install APK") }
                    TextButton(onClick = { onSelect(release.projectId) }) { Text("View project") }
                }
            }
        }
    }
}

@Composable
private fun SettingsPage(outputConnected: Boolean, onConnect: () -> Unit, onDisconnect: () -> Unit,
                         githubAccount: String?) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Build engine", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text("Offline-first • No account • No subscription • GitHub optional", color = TextSoft)
        ContentPanel {
            Text("BUILD OUTPUT FOLDER", fontWeight = FontWeight.Bold, color = Accent)
            Text(if (outputConnected) "Connected to Downloads / BuildDeck / Out" else "Not connected. Automatic APK collection requires read-only folder access.", color = TextSoft)
            Spacer(Modifier.height(8.dp))
            Button(onClick = onConnect) { Text(if (outputConnected) "Change output folder" else "Connect output folder") }
            if (outputConnected) TextButton(onClick = onDisconnect) { Text("Disconnect folder") }
        }
        ContentPanel {
            Text("GitHub: " + (githubAccount?.let { "Connected @$it" } ?: "Not connected"), color = TextSoft)
            Text("Use the Projects tab to import a repository or connect a fine-grained token.", color = TextSoft)
        }
        ContentPanel {
            Text("LOCAL BUILDER REQUIREMENTS", fontWeight = FontWeight.Bold, color = Accent)
            Spacer(Modifier.height(8.dp))
            Text("1. Install Termux from an official maintained distribution.")
            Text("2. In Termux, run termux-setup-storage and configure OpenJDK, Android SDK, Gradle and ARM-compatible aapt2.")
            Text("3. Set allow-external-apps=true in ~/.termux/termux.properties.")
            Text("4. Grant BuildDeck permission to run Termux commands in Android Settings.")
            Text("5. Import a complete Gradle project ZIP, open it, then tap Build APK.")
            Text("6. When the build finishes, import its APK from Downloads/BuildDeck/Out/ into that project's release library.", color = TextSoft)
        }
        ContentPanel {
            Text("Important", fontWeight = FontWeight.Bold)
            Text("Only build trusted code. Keep a private, off-device backup of the Termux ~/.android/debug.keystore for future debug updates. Never upload signing keys to a public repo. Production signing is not implemented.", color = TextSoft)
        }
        ContentPanel {
            Text("BuildDeck v0.3.3 • File Manager Bridge", fontWeight = FontWeight.Bold)
            Text("Source ZIP validation prevents building APK resources as source. GitHub change review, rollback, and local builds remain available. Direct remote ChatGPT phone access is not enabled.", color = TextSoft)
        }
    }
}

@Composable
private fun ContentPanel(content: @Composable ColumnScope.() -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Panel),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFF293A51)),
        modifier = Modifier.fillMaxWidth()
    ) { Column(Modifier.padding(15.dp), content = content) }
}

private fun formatDate(timestamp: Long): String = SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(timestamp))

