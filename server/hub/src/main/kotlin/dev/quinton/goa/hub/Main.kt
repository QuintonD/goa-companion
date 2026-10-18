package dev.quinton.goa.hub

import kotlin.system.exitProcess

fun main(args: Array<String>) {
    val code = HubCli(out = System.out, err = System.err).run(args.toList())
    if (code != HubCli.SERVING) exitProcess(code)
}
