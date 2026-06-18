package ai.desertant.emoexample

import ai.desertant.emo.Emo
import android.content.Context
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class Todo(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val emoji: String,
)

/** Persists the todo list to SharedPreferences as JSON — mirrors the iOS `TodoStore`. */
class TodoStore(context: Context) {
    private val prefs = context.getSharedPreferences("emo_todos", Context.MODE_PRIVATE)
    private val key = "todos"
    private val serializer = ListSerializer(Todo.serializer())

    val todos = mutableStateListOf<Todo>()

    init {
        val data = prefs.getString(key, null)
        if (data != null) {
            runCatching { Json.decodeFromString(serializer, data) }
                .getOrNull()
                ?.let { todos.addAll(it) }
        }
    }

    fun add(todo: Todo) {
        todos.add(todo)
        save()
    }

    fun remove(todo: Todo) {
        todos.removeAll { it.id == todo.id }
        save()
    }

    private fun save() {
        prefs.edit().putString(key, Json.encodeToString(serializer, todos.toList())).apply()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentView() {
    val context = LocalContext.current
    val store = remember { TodoStore(context.applicationContext) }
    var showingAdd by remember { mutableStateOf(false) }

    if (showingAdd) {
        // Presented in place of the list (like the iOS sheet) so its own Scaffold
        // receives the full window insets and the top bar clears the status bar.
        AddTodoView(
            onDismiss = { showingAdd = false },
            onSave = { todo ->
                store.add(todo)
                showingAdd = false
            },
        )
        return
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Todo") }, windowInsets = WindowInsets(0, 0, 0, 0)) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showingAdd = true }) {
                Icon(Icons.Default.Add, contentDescription = "Add Todo")
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (store.todos.isEmpty()) {
                EmptyState()
            } else {
                TodoList(store)
            }
        }
    }
}

@Composable
private fun TodoList(store: TodoStore) {
    LazyColumn(Modifier.fillMaxSize()) {
        items(store.todos, key = { it.id }) { todo ->
            TodoRow(
                todo = todo,
                onComplete = { store.remove(todo) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("✨", fontSize = 64.sp, modifier = Modifier.alpha(0.4f))
        Spacer(Modifier.size(12.dp))
        Text(
            "No todos yet",
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(4.dp))
        Text(
            "Tap + to add one",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun TodoRow(
    todo: Todo,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var completing by remember { mutableStateOf(false) }

    LaunchedEffect(completing) {
        if (completing) {
            delay(320)
            onComplete()
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .alpha(if (completing) 0.6f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            todo.emoji,
            fontSize = 30.sp,
            modifier = Modifier
                .width(40.dp)
                .scale(if (completing) 0.7f else 1f)
                .alpha(if (completing) 0f else 1f),
        )
        Spacer(Modifier.size(14.dp))
        Text(
            todo.title,
            fontSize = 17.sp,
            textDecoration = if (completing) TextDecoration.LineThrough else null,
            color = if (completing) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .then(
                    if (completing) {
                        Modifier.background(MaterialTheme.colorScheme.primary)
                    } else {
                        Modifier.border(
                            1.5.dp,
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            CircleShape,
                        )
                    },
                )
                .clickable(enabled = !completing) { completing = true },
            contentAlignment = Alignment.Center,
        ) {
            if (completing) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTodoView(
    onDismiss: () -> Unit,
    onSave: (Todo) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("✨") }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()

    val trimmed = title.trim()

    // Live prediction, debounced 200 ms. Re-keying on `title` cancels the
    // previous in-flight prediction — same behavior as the iOS example.
    LaunchedEffect(title) {
        if (trimmed.isEmpty()) {
            emoji = "✨"
            return@LaunchedEffect
        }
        delay(200)
        val next = Emo.suggestions(trimmed, limit = 1).firstOrNull()?.emoji
        if (next != null) emoji = next
    }

    fun save() {
        val t = title.trim()
        if (t.isEmpty()) return
        scope.launch {
            val predicted = Emo.suggestions(t, limit = 1).firstOrNull()?.emoji
            onSave(Todo(title = t, emoji = predicted ?: emoji))
        }
    }

    BackHandler(onBack = onDismiss)

    Surface(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = { Text("New Todo") },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    navigationIcon = { TextButton(onClick = onDismiss) { Text("Cancel") } },
                    actions = {
                        TextButton(onClick = { save() }, enabled = trimmed.isNotEmpty()) {
                            Text("Save", fontWeight = FontWeight.SemiBold)
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                EmojiBadge(emoji = emoji, dimmed = trimmed.isEmpty())

                Spacer(Modifier.size(40.dp))

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = {
                        Text("What's on your list?", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        textAlign = TextAlign.Center,
                        fontFamily = FontFamily.Default,
                        fontWeight = FontWeight.Medium,
                    ),
                    shape = RoundedCornerShape(18.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        keyboard?.hide()
                        save()
                    }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .focusRequester(focusRequester),
                )
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
}

@Composable
private fun EmojiBadge(emoji: String, dimmed: Boolean) {
    Box(
        modifier = Modifier
            .size(168.dp)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                    ),
                ),
            )
            .border(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = emoji,
            transitionSpec = {
                (scaleIn(spring(stiffness = Spring.StiffnessLow), initialScale = 0.5f) + fadeIn())
                    .togetherWith(scaleOut(tween(150), targetScale = 0.5f) + fadeOut(tween(150)))
            },
            label = "emoji",
        ) { value ->
            Text(value, fontSize = 72.sp, modifier = Modifier.alpha(if (dimmed) 0.5f else 1f))
        }
    }
}
