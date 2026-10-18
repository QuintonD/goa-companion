package dev.quinton.goa.feature.sync

/**
 * What this phone is called on the other phones until its owner picks themselves in "Who are you?"
 * (ADR-025). On Android it is the phone's own name ("Galaxy S25 Ultra", or what the owner renamed it
 * to), not `Build.MODEL`, which is a code such as "SM-S938B".
 */
fun interface PhoneName {
    fun get(): String
}
