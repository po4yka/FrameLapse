package com.po4yka.framelapse.domain.service

import com.po4yka.framelapse.domain.entity.AdjustmentCommand
import com.po4yka.framelapse.domain.entity.ManualAdjustment
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages undo/redo history for manual adjustments within an editing session.
 *
 * Design:
 * - In-memory stack for current session performance
 * - Maximum history size to prevent memory issues
 * - Branch handling: new action after undo clears redo stack
 * - Observable state for UI updates
 *
 * @param maxHistorySize Maximum number of commands to keep in history (default: 50)
 */
class UndoRedoManager(private val maxHistorySize: Int = DEFAULT_MAX_HISTORY_SIZE) {

    private val undoStack = ArrayDeque<AdjustmentCommand>()
    private val redoStack = ArrayDeque<AdjustmentCommand>()

    private val _canUndo = MutableStateFlow(false)

    /** Observable state indicating whether undo is available. */
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)

    /** Observable state indicating whether redo is available. */
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _historySize = MutableStateFlow(0)

    /** Observable current history size. */
    val historySize: StateFlow<Int> = _historySize.asStateFlow()

    /**
     * Pushes a new command onto the undo stack.
     * Clears the redo stack (new action branches from current state).
     * Trims history if it exceeds the maximum size.
     *
     * @param command The command to push.
     */
    fun pushCommand(command: AdjustmentCommand) {
        undoStack.addLast(command)
        redoStack.clear()

        // Trim history if exceeding max size
        while (undoStack.size > maxHistorySize) {
            undoStack.removeFirst()
        }

        updateState()
    }

    /**
     * Undoes the last command and returns the previous adjustment state.
     * Moves the command to the redo stack.
     *
     * @return The previous ManualAdjustment state, or null if nothing to undo.
     */
    fun undo(): ManualAdjustment? {
        val command = undoStack.removeLastOrNull() ?: return null
        redoStack.addLast(command)
        updateState()
        return command.undo()
    }

    /**
     * Redoes the last undone command and returns the resulting adjustment state.
     * Moves the command back to the undo stack.
     *
     * @return The redone ManualAdjustment state, or null if nothing to redo.
     */
    fun redo(): ManualAdjustment? {
        val command = redoStack.removeLastOrNull() ?: return null
        undoStack.addLast(command)
        updateState()
        return command.execute()
    }

    /**
     * Clears all history (both undo and redo stacks).
     */
    fun clear() {
        undoStack.clear()
        redoStack.clear()
        updateState()
    }

    /**
     * Gets the description of the command that would be undone.
     * Useful for showing "Undo: Move Left Eye" in UI.
     *
     * @return The description or null if nothing to undo.
     */
    fun getUndoDescription(): String? = undoStack.lastOrNull()?.description

    /**
     * Gets the description of the command that would be redone.
     * Useful for showing "Redo: Move Left Eye" in UI.
     *
     * @return The description or null if nothing to redo.
     */
    fun getRedoDescription(): String? = redoStack.lastOrNull()?.description

    /**
     * Gets all commands in the undo stack (oldest first).
     * Useful for debugging or displaying history.
     */
    fun getUndoHistory(): List<AdjustmentCommand> = undoStack.toList()

    /**
     * Gets all commands in the redo stack (oldest first).
     */
    fun getRedoHistory(): List<AdjustmentCommand> = redoStack.toList()

    /**
     * Gets the total number of commands in both stacks.
     */
    fun getTotalHistorySize(): Int = undoStack.size + redoStack.size

    /**
     * Checks if there are any unsaved changes (commands in the undo stack).
     */
    fun hasUnsavedChanges(): Boolean = undoStack.isNotEmpty()

    private fun updateState() {
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
        _historySize.value = undoStack.size
    }

    companion object {
        const val DEFAULT_MAX_HISTORY_SIZE = 50
    }
}
