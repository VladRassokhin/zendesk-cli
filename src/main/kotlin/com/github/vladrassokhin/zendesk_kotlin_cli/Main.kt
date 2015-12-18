package com.github.vladrassokhin.zendesk_kotlin_cli

import com.sampullara.cli.Args
import com.sampullara.cli.Argument
import org.zendesk.client.v2.Zendesk
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

public class Main {
    @set:Argument(description = "Zendesk host")
    var host = "https://support.zendesk.com/"

    @set:Argument()
    var username: String? = null

    @set:Argument()
    var password: String? = null

    @set:Argument()
    var token: String? = null

    @set:Argument()
    var config: String? = null

    val commands: List<Command> by lazy {
        val loader = java.util.ServiceLoader.load(Command::class.java)
        return@lazy loader.toArrayList()
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val options = Main()
            val code = options.run(args);
            System.exit(code);
        }


        public fun err(text: String): Int {
            System.err.println(text)
            return 1
        }
    }

    fun run(args: Array<String>): Int {
        val commands: List<String>
        try {
            commands = Args.parse(this, args, false)
        } catch (e: IllegalArgumentException) {
            System.err.println(e.message)
            Args.usage(this)
            return 1
        }
        if (config != null) {
            if (populateFromConfig(File(config))) return 1
        } else {
            val path = Paths.get(System.getProperty("user.home"), ".zendesk-cli-config")
            if (Files.exists(path)) {
                if (populateFromConfig(path.toFile())) return 1
            }
        }
        if (commands.isEmpty()) {
            return err("Expected command name. Supported commands:" + this.commands.joinToString ("\n\t", "\n\t") { it.name })
        }
        if (token == null && password == null) {
            err("Either password or token should be set")
            Args.usage(this)
            return 1
        }
        val cmd = commands.first()
        val command = this.commands.find { it.name == cmd } ?: return err("Unknown command $cmd")

        // Let's configure client
        val builder = Zendesk.Builder(host);
        builder.setUsername(username)
        if (token != null) {
            builder.setToken(token)
        } else if (password != null) {
            builder.setPassword(password)
        }
        val client = builder.build()

        try {
            return command.run(this, commands.drop(1), client)
        } catch(e: Exception) {
            err("Exception during executing command $cmd")
            e.printStackTrace()
            return 1
        }
    }

    private fun populateFromConfig(toFile: File): Boolean {
        val lines = toFile.readLines("UTF-8")
        try {
            Args.parse(this, lines.toTypedArray(), false)
        } catch (e: IllegalArgumentException) {
            System.err.println(e.message)
            Args.usage(this)
            return true
        }
        return false
    }
}