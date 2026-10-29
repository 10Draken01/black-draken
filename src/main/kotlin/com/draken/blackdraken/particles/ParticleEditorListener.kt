package com.draken.blackdraken.particles

import com.draken.blackdraken.settings.BlackDrakenSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key

private class EditorEffects(
    val background: EditorBackgroundOverlay,
    val particles: ParticleOverlay,
    val disposable: Disposable
) {
    fun dispose() {
        particles.dispose()
        background.dispose()
        Disposer.dispose(disposable)
    }
}

private val EFFECTS_KEY = Key.create<EditorEffects>("BlackDraken.Effects")

class ParticleEditorListener : EditorFactoryListener {

    override fun editorCreated(event: EditorFactoryEvent) {
        val editor = event.editor

        if (editor.editorKind != EditorKind.MAIN_EDITOR) return

        val disposable = Disposer.newDisposable("BlackDrakenEffects")

        // Primero el fondo y luego las partículas
        val background = EditorBackgroundOverlay(editor, disposable)
        val particles = ParticleOverlay(editor)

        // Swing pinta primero el índice más alto: partículas (0) quedan ENCIMA
        editor.contentComponent.setComponentZOrder(particles, 0)

        editor.document.addDocumentListener(
            object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    val settings = BlackDrakenSettings.getInstance().state

                    if (!settings.enabled) return
                    if (editor.isDisposed || editor.isViewer) return
                    if (!editor.contentComponent.hasFocus()) return

                    // Solo escritura de un carácter
                    val typed = event.oldLength == 0 && event.newLength == 1
                    if (!typed) return

                    val offset = (event.offset + event.newLength)
                        .coerceIn(0, editor.document.textLength)

                    val point = editor.offsetToXY(offset)
                    val x = point.x
                    val y = point.y + editor.lineHeight / 2

                    val isEnter = event.newFragment.firstOrNull() == '\n'

                    if (isEnter) {
                        particles.slash(x, y)
                        background.kick()
                    } else {
                        particles.emit(x, y, settings.intensity)
                    }
                }
            },
            disposable
        )

        editor.putUserData(EFFECTS_KEY, EditorEffects(background, particles, disposable))
    }

    override fun editorReleased(event: EditorFactoryEvent) {
        val editor = event.editor
        editor.getUserData(EFFECTS_KEY)?.dispose()
        editor.putUserData(EFFECTS_KEY, null)
    }
}