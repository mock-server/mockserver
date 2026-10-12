package com.mockserver.jetbrains

import com.intellij.json.JsonFileType
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightVirtualFile

/** Shared helper for opening MockServer responses in a new in-memory editor tab. */
object MockServerEditors {

    /**
     * Open [content] in a new, non-persisted JSON editor tab named [fileName].
     * Must be called on the EDT.
     */
    fun openJsonInEditor(project: Project, fileName: String, content: String) {
        val virtualFile = LightVirtualFile(fileName, JsonFileType.INSTANCE, content)
        OpenFileDescriptor(project, virtualFile).navigate(true)
    }

    /**
     * Open [content] in a new, non-persisted plain-text editor tab named [fileName].
     * Used for free-form output (e.g. an HTTP response summary) that is not
     * necessarily JSON. Must be called on the EDT.
     */
    fun openTextInEditor(project: Project, fileName: String, content: String) {
        val virtualFile = LightVirtualFile(fileName, PlainTextFileType.INSTANCE, content)
        OpenFileDescriptor(project, virtualFile).navigate(true)
    }
}
