package mba.vm.onhit.core.emulator

import mba.vm.onhit.core.model.TagTechnology
import kotlin.random.Random

data class FakeTagSession (
    var connectedTechnology: TagTechnology = TagTechnology.Unknown,
    val handle: Int = Random.nextInt(Int.MAX_VALUE)
)
