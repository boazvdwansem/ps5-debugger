package com.osr.ps5debugger.ports.outbound

import com.osr.ps5debugger.domain.model.GameCheatProfile

interface CheatStoragePort {
    fun saveCheats(profiles: List<GameCheatProfile>)
    fun loadCheats(): List<GameCheatProfile>
}
