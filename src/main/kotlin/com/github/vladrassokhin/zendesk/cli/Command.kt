package com.github.vladrassokhin.zendesk.cli

import org.zendesk.client.v2.Zendesk

public interface Command {
    val name: String;
    fun run(options: Main, args: List<String>, client: Zendesk): Int;
}