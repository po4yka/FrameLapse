---
name: compose-command-pattern
description: Implements command pattern for undo/redo functionality and action history. Use when building editors, forms with undo support, or action logging.
---

# Compose Command Pattern

## Overview

The Command pattern encapsulates actions as objects, enabling undo/redo functionality, action queuing, and history tracking. This is particularly useful for editors, drawing apps, and any feature requiring reversible operations.

## When to Use

- Implementing undo/redo for editors
- Building action history/log
- Queuing operations for batch processing
- Creating macro/script recording

## 1. Basic Command Pattern

### Command Interface

```kotlin
// domain/command/Command.kt

interface Command {
    fun execute()
    fun undo()
    fun getDescription(): String
}
```

### Text Editor Command Example

```kotlin
// presentation/command/TextCommand.kt

class UpdateTextCommand(
    private val oldText: String,
    private val newText: String,
    private val onTextChanged: (String) -> Unit,
) : Command {

    override fun execute() {
        onTextChanged(newText)
    }

    override fun undo() {
        onTextChanged(oldText)
    }

    override fun getDescription(): String = "Update text"
}

class InsertTextCommand(
    private val textToInsert: String,
    private val position: Int,
    private val getCurrentText: () -> String,
    private val onTextChanged: (String) -> Unit,
) : Command {

    override fun execute() {
        val current = getCurrentText()
        val newText = current.substring(0, position) + textToInsert + current.substring(position)
        onTextChanged(newText)
    }

    override fun undo() {
        val current = getCurrentText()
        val newText = current.substring(0, position) + current.substring(position + textToInsert.length)
        onTextChanged(newText)
    }

    override fun getDescription(): String = "Insert '$textToInsert'"
}

class DeleteTextCommand(
    private val startPosition: Int,
    private val endPosition: Int,
    private val getCurrentText: () -> String,
    private val onTextChanged: (String) -> Unit,
) : Command {

    private var deletedText: String = ""

    override fun execute() {
        val current = getCurrentText()
        deletedText = current.substring(startPosition, endPosition)
        val newText = current.substring(0, startPosition) + current.substring(endPosition)
        onTextChanged(newText)
    }

    override fun undo() {
        val current = getCurrentText()
        val newText = current.substring(0, startPosition) + deletedText + current.substring(startPosition)
        onTextChanged(newText)
    }

    override fun getDescription(): String = "Delete text"
}
```

### Command Controller

```kotlin
// presentation/command/CommandController.kt

class CommandController {

    private val history = mutableListOf<Command>()
    private var currentIndex = -1

    val canUndo: Boolean
        get() = currentIndex >= 0

    val canRedo: Boolean
        get() = currentIndex < history.size - 1

    fun execute(command: Command) {
        // Remove any commands after current position (discard redo history)
        if (currentIndex < history.size - 1) {
            history.subList(currentIndex + 1, history.size).clear()
        }

        // Add and execute new command
        history.add(command)
        currentIndex++
        command.execute()
    }

    fun undo(): Boolean {
        if (!canUndo) return false

        history[currentIndex].undo()
        currentIndex--
        return true
    }

    fun redo(): Boolean {
        if (!canRedo) return false

        currentIndex++
        history[currentIndex].execute()
        return true
    }

    fun clear() {
        history.clear()
        currentIndex = -1
    }

    fun getHistory(): List<String> {
        return history.take(currentIndex + 1).map { it.getDescription() }
    }
}
```

### Usage in Composable

```kotlin
// presentation/editor/TextEditorScreen.kt

@Composable
fun TextEditorScreen() {
    var text by remember { mutableStateOf("") }
    val controller = remember { CommandController() }

    // Force recomposition when undo/redo state changes
    var undoState by remember { mutableStateOf(false) }
    var redoState by remember { mutableStateOf(false) }

    fun updateUndoRedoState() {
        undoState = controller.canUndo
        redoState = controller.canRedo
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Text Editor") },
                actions = {
                    IconButton(
                        onClick = {
                            controller.undo()
                            updateUndoRedoState()
                        },
                        enabled = undoState,
                    ) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo")
                    }
                    IconButton(
                        onClick = {
                            controller.redo()
                            updateUndoRedoState()
                        },
                        enabled = redoState,
                    ) {
                        Icon(Icons.Default.Redo, contentDescription = "Redo")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            TextField(
                value = text,
                onValueChange = { newText ->
                    if (newText != text) {
                        controller.execute(
                            UpdateTextCommand(
                                oldText = text,
                                newText = newText,
                                onTextChanged = { text = it },
                            )
                        )
                        updateUndoRedoState()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        }
    }
}
```

## 2. Frame Editor Commands for FrameLapse

### Frame Manipulation Commands

```kotlin
// domain/command/FrameCommand.kt

sealed interface FrameCommand : Command {
    val projectId: String
}

class DeleteFrameCommand(
    override val projectId: String,
    private val frameId: String,
    private val frameRepository: FrameRepository,
    private val fileManager: FileManager,
) : FrameCommand {

    private var deletedFrame: Frame? = null
    private var deletedFilePath: String? = null

    override fun execute() {
        runBlocking {
            // Store for undo
            deletedFrame = frameRepository.getFrame(frameId).getOrNull()
            deletedFilePath = deletedFrame?.originalPath

            // Actually delete (but keep file in trash)
            deletedFrame?.let { frame ->
                fileManager.moveToTrash(frame.originalPath)
                frame.alignedPath?.let { fileManager.moveToTrash(it) }
            }
            frameRepository.deleteFrame(frameId)
        }
    }

    override fun undo() {
        runBlocking {
            deletedFrame?.let { frame ->
                // Restore file from trash
                deletedFilePath?.let { fileManager.restoreFromTrash(it) }
                frame.alignedPath?.let { fileManager.restoreFromTrash(it) }

                // Restore database entry
                frameRepository.addFrame(frame)
            }
        }
    }

    override fun getDescription(): String = "Delete frame"
}

class ReorderFrameCommand(
    override val projectId: String,
    private val frameId: String,
    private val fromPosition: Int,
    private val toPosition: Int,
    private val frameRepository: FrameRepository,
) : FrameCommand {

    override fun execute() {
        runBlocking {
            frameRepository.updateSortOrder(frameId, toPosition)
            // Update other frames' sort orders as needed
            shiftFrames(fromPosition, toPosition)
        }
    }

    override fun undo() {
        runBlocking {
            frameRepository.updateSortOrder(frameId, fromPosition)
            shiftFrames(toPosition, fromPosition)
        }
    }

    private suspend fun shiftFrames(from: Int, to: Int) {
        val frames = frameRepository.getFramesByProject(projectId).getOrNull() ?: return

        if (from < to) {
            // Shift frames down
            frames.filter { it.sortOrder in (from + 1)..to }
                .forEach { frame ->
                    frameRepository.updateSortOrder(frame.id, frame.sortOrder - 1)
                }
        } else {
            // Shift frames up
            frames.filter { it.sortOrder in to until from }
                .forEach { frame ->
                    frameRepository.updateSortOrder(frame.id, frame.sortOrder + 1)
                }
        }
    }

    override fun getDescription(): String = "Reorder frame"
}

class BatchDeleteFramesCommand(
    override val projectId: String,
    private val frameIds: List<String>,
    private val frameRepository: FrameRepository,
    private val fileManager: FileManager,
) : FrameCommand {

    private val deletedFrames = mutableListOf<Frame>()

    override fun execute() {
        runBlocking {
            frameIds.forEach { frameId ->
                frameRepository.getFrame(frameId).getOrNull()?.let { frame ->
                    deletedFrames.add(frame)
                    fileManager.moveToTrash(frame.originalPath)
                    frame.alignedPath?.let { fileManager.moveToTrash(it) }
                    frameRepository.deleteFrame(frameId)
                }
            }
        }
    }

    override fun undo() {
        runBlocking {
            deletedFrames.forEach { frame ->
                fileManager.restoreFromTrash(frame.originalPath)
                frame.alignedPath?.let { fileManager.restoreFromTrash(it) }
                frameRepository.addFrame(frame)
            }
            deletedFrames.clear()
        }
    }

    override fun getDescription(): String = "Delete ${frameIds.size} frames"
}
```

### Frame Editor Controller

```kotlin
// presentation/gallery/FrameEditorController.kt

class FrameEditorController(
    private val projectId: String,
    private val frameRepository: FrameRepository,
    private val fileManager: FileManager,
) {
    private val commandController = CommandController()

    val canUndo: Boolean
        get() = commandController.canUndo

    val canRedo: Boolean
        get() = commandController.canRedo

    fun deleteFrame(frameId: String) {
        commandController.execute(
            DeleteFrameCommand(
                projectId = projectId,
                frameId = frameId,
                frameRepository = frameRepository,
                fileManager = fileManager,
            )
        )
    }

    fun deleteFrames(frameIds: List<String>) {
        if (frameIds.size == 1) {
            deleteFrame(frameIds.first())
        } else {
            commandController.execute(
                BatchDeleteFramesCommand(
                    projectId = projectId,
                    frameIds = frameIds,
                    frameRepository = frameRepository,
                    fileManager = fileManager,
                )
            )
        }
    }

    fun reorderFrame(frameId: String, fromPosition: Int, toPosition: Int) {
        commandController.execute(
            ReorderFrameCommand(
                projectId = projectId,
                frameId = frameId,
                fromPosition = fromPosition,
                toPosition = toPosition,
                frameRepository = frameRepository,
            )
        )
    }

    fun undo() = commandController.undo()
    fun redo() = commandController.redo()
    fun getHistory() = commandController.getHistory()
}
```

## 3. Export Settings Commands

### Preset Commands

```kotlin
// presentation/command/ExportSettingsCommand.kt

class ApplyExportPresetCommand(
    private val preset: ExportPreset,
    private val getCurrentSettings: () -> ExportSettings,
    private val onSettingsChanged: (ExportSettings) -> Unit,
) : Command {

    private lateinit var previousSettings: ExportSettings

    override fun execute() {
        previousSettings = getCurrentSettings()
        onSettingsChanged(preset.toSettings())
    }

    override fun undo() {
        onSettingsChanged(previousSettings)
    }

    override fun getDescription(): String = "Apply ${preset.name} preset"
}

class UpdateResolutionCommand(
    private val newResolution: Resolution,
    private val getCurrentSettings: () -> ExportSettings,
    private val onSettingsChanged: (ExportSettings) -> Unit,
) : Command {

    private lateinit var previousResolution: Resolution

    override fun execute() {
        val current = getCurrentSettings()
        previousResolution = current.resolution
        onSettingsChanged(current.copy(resolution = newResolution))
    }

    override fun undo() {
        val current = getCurrentSettings()
        onSettingsChanged(current.copy(resolution = previousResolution))
    }

    override fun getDescription(): String = "Change resolution to ${newResolution.name}"
}
```

## 4. Macro Recording

### Recording Multiple Commands

```kotlin
// presentation/command/MacroRecorder.kt

class MacroRecorder {

    private val recordedCommands = mutableListOf<Command>()
    private var isRecording = false

    fun startRecording() {
        recordedCommands.clear()
        isRecording = true
    }

    fun stopRecording(): MacroCommand? {
        isRecording = false
        return if (recordedCommands.isNotEmpty()) {
            MacroCommand(recordedCommands.toList())
        } else {
            null
        }
    }

    fun recordCommand(command: Command) {
        if (isRecording) {
            recordedCommands.add(command)
        }
    }

    fun isRecording() = isRecording
}

class MacroCommand(
    private val commands: List<Command>,
) : Command {

    override fun execute() {
        commands.forEach { it.execute() }
    }

    override fun undo() {
        commands.reversed().forEach { it.undo() }
    }

    override fun getDescription(): String = "Macro (${commands.size} actions)"
}
```

### Usage with Macro Recording

```kotlin
@Composable
fun EditorWithMacro(
    controller: FrameEditorController,
    macroRecorder: MacroRecorder,
) {
    var isRecording by remember { mutableStateOf(false) }
    var savedMacro by remember { mutableStateOf<MacroCommand?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Frame Editor") },
                actions = {
                    // Undo/Redo
                    IconButton(
                        onClick = { controller.undo() },
                        enabled = controller.canUndo,
                    ) {
                        Icon(Icons.Default.Undo, contentDescription = "Undo")
                    }
                    IconButton(
                        onClick = { controller.redo() },
                        enabled = controller.canRedo,
                    ) {
                        Icon(Icons.Default.Redo, contentDescription = "Redo")
                    }

                    // Macro recording
                    IconButton(
                        onClick = {
                            if (isRecording) {
                                savedMacro = macroRecorder.stopRecording()
                                isRecording = false
                            } else {
                                macroRecorder.startRecording()
                                isRecording = true
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                            contentDescription = if (isRecording) "Stop recording" else "Record macro",
                            tint = if (isRecording) Color.Red else LocalContentColor.current,
                        )
                    }

                    // Play macro
                    savedMacro?.let { macro ->
                        IconButton(onClick = { macro.execute() }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play macro")
                        }
                    }
                }
            )
        }
    ) { padding ->
        // Editor content
    }
}
```

## 5. ViewModel Integration

### Command-Enabled ViewModel

```kotlin
// presentation/gallery/GalleryViewModel.kt

class GalleryViewModel(
    private val frameRepository: FrameRepository,
    private val fileManager: FileManager,
) : BaseViewModel<GalleryState, GalleryEvent, GalleryEffect>(GalleryState()) {

    private var editorController: FrameEditorController? = null

    val canUndo: Boolean
        get() = editorController?.canUndo ?: false

    val canRedo: Boolean
        get() = editorController?.canRedo ?: false

    override fun onEvent(event: GalleryEvent) {
        when (event) {
            is GalleryEvent.Initialize -> initialize(event.projectId)
            is GalleryEvent.DeleteSelected -> deleteSelected()
            is GalleryEvent.ReorderFrame -> reorderFrame(event.frameId, event.from, event.to)
            GalleryEvent.Undo -> undo()
            GalleryEvent.Redo -> redo()
            // ...
        }
    }

    private fun initialize(projectId: String) {
        editorController = FrameEditorController(
            projectId = projectId,
            frameRepository = frameRepository,
            fileManager = fileManager,
        )
        loadFrames(projectId)
    }

    private fun deleteSelected() {
        val selectedIds = state.value.selectedFrameIds.toList()
        if (selectedIds.isEmpty()) return

        editorController?.deleteFrames(selectedIds)
        updateState { copy(selectedFrameIds = emptySet()) }

        // Reload frames
        loadFrames(state.value.projectId)

        // Notify state change for undo/redo buttons
        sendEffect(GalleryEffect.UndoRedoStateChanged)
    }

    private fun reorderFrame(frameId: String, from: Int, to: Int) {
        editorController?.reorderFrame(frameId, from, to)
        loadFrames(state.value.projectId)
        sendEffect(GalleryEffect.UndoRedoStateChanged)
    }

    private fun undo() {
        editorController?.undo()
        loadFrames(state.value.projectId)
        sendEffect(GalleryEffect.UndoRedoStateChanged)
    }

    private fun redo() {
        editorController?.redo()
        loadFrames(state.value.projectId)
        sendEffect(GalleryEffect.UndoRedoStateChanged)
    }
}
```

## Anti-Patterns

### Avoid: Command Without Proper State Capture

```kotlin
// BAD - captures reference, not value
class BadCommand(
    private val frame: Frame,  // Reference might change!
    private val repository: FrameRepository,
) : Command {
    override fun undo() {
        repository.addFrame(frame)  // frame might have been modified
    }
}

// BETTER - capture immutable copy
class GoodCommand(
    private val frameSnapshot: Frame,  // Immutable copy at execution time
    private val repository: FrameRepository,
) : Command {
    override fun undo() {
        repository.addFrame(frameSnapshot)  // Restores exact state
    }
}
```

### Avoid: Async Commands Without Proper Handling

```kotlin
// BAD - async without proper management
class BadAsyncCommand : Command {
    override fun execute() {
        GlobalScope.launch {  // Fire and forget
            // ... async operation
        }
    }
}

// BETTER - synchronous or properly awaited
class GoodCommand(
    private val scope: CoroutineScope,
) : Command {
    override fun execute() {
        runBlocking {  // Or use proper scope management
            // ... operation
        }
    }
}
```

## Reference Examples

- Use case pattern: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/domain/usecase/`
- ViewModel base: `composeApp/src/commonMain/kotlin/com/po4yka/framelapse/presentation/base/BaseViewModel.kt`

## Checklist

### Command Interface
- [ ] `execute()` performs the action
- [ ] `undo()` reverses the action completely
- [ ] `getDescription()` returns human-readable description
- [ ] Captures all necessary state at creation

### Controller
- [ ] Maintains command history
- [ ] Tracks current position for undo/redo
- [ ] Clears redo history on new command
- [ ] Exposes canUndo/canRedo state

### State Management
- [ ] Captures state before modification
- [ ] Uses immutable copies for state snapshots
- [ ] Handles async operations properly
- [ ] Updates UI after undo/redo

### Integration
- [ ] ViewModel exposes undo/redo capability
- [ ] UI binds to canUndo/canRedo state
- [ ] Commands are created with proper dependencies
- [ ] History is cleared when appropriate (e.g., project change)
