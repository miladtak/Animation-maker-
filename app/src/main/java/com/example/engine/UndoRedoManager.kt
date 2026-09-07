package com.example.engine

import com.example.model.Project
import java.util.ArrayDeque

class UndoRedoManager(private val maxHistory: Int = 100) {

    private val undoStack = ArrayDeque<Project>()
    private val redoStack = ArrayDeque<Project>()

    fun recordState(currentState: Project) {
        if (undoStack.size >= maxHistory) {
            undoStack.removeLast()
        }
        undoStack.push(currentState)
        redoStack.clear()
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()

    fun canRedo(): Boolean = redoStack.isNotEmpty()

    fun undo(currentState: Project): Project? {
        if (!canUndo()) return null
        redoStack.push(currentState)
        return undoStack.pop()
    }

    fun redo(currentState: Project): Project? {
        if (!canRedo()) return null
        undoStack.push(currentState)
        return redoStack.pop()
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}
