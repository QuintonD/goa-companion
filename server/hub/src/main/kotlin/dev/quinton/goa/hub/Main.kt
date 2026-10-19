package dev.quinton.goa.hub

import kotlin.system.exitProcess

fun main(args: Array<String>) {
    // Encoding-safe on every console (CliOutput): UTF-8, or ASCII only on Windows.
    val code = HubCli(out = CliOutput.stream(System.out), err = CliOutput.stream(System.err)).run(args.toList())
    if (code != HubCli.SERVING) exitProcess(code)
}
