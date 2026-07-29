package mba.vm.onhit.hook.core.emulator

import mba.vm.onhit.model.TagTechnology
import kotlin.random.Random

data class FakeTagSession (
    var connectedTechnology: TagTechnology = TagTechnology.Unknown,
    val handle: Int = Random.nextInt(Int.MAX_VALUE)
)
