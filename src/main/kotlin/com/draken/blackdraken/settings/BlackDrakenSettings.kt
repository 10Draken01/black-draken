package com.draken.blackdraken.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@Service(Service.Level.APP)
@State(name = "BlackDrakenSettings", storages = [Storage("themes/BlackDraken.xml")])
class BlackDrakenSettings : PersistentStateComponent<BlackDrakenSettings.State> {

    class State {
        var enabled: Boolean = true          // partículas al escribir
        var intensity: Int = 3               // partículas por tecla (1-12)
        var backgroundEnabled: Boolean = true
        var backgroundOpacity: Int = 12      // % (1-40)
        var glitchEnabled: Boolean = true
    }

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        fun getInstance(): BlackDrakenSettings = service()
    }
}