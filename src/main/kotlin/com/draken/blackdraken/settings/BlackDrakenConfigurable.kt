package com.draken.blackdraken.settings

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel

class BlackDrakenConfigurable : BoundConfigurable("Black Draken") {

    override fun createPanel() = panel {
        val state = BlackDrakenSettings.getInstance().state

        group("Typing particles") {
            row {
                checkBox("Enable typing particles").bindSelected(state::enabled)
            }
            row("Particles per keystroke (1-12):") {
                intTextField(1..12).bindIntText(state::intensity)
            }
        }

        group("Editor background") {
            row {
                checkBox("Show demon background image").bindSelected(state::backgroundEnabled)
            }
            row("Opacity % (1-40):") {
                intTextField(1..40).bindIntText(state::backgroundOpacity)
            }
            row {
                checkBox("Corruption / glitch bursts").bindSelected(state::glitchEnabled)
            }
        }
    }
}