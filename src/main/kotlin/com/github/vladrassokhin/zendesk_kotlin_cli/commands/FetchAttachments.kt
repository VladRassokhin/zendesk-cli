package com.github.vladrassokhin.zendesk_kotlin_cli.commands

import com.github.vladrassokhin.zendesk_kotlin_cli.Main
import com.github.vladrassokhin.zendesk_kotlin_cli.Main.Companion.err
import com.ning.http.client.AsyncHttpClient
import com.ning.http.client.AsyncHttpClientConfig
import com.ning.http.client.ListenableFuture
import com.ning.http.client.Realm
import com.ning.http.client.extra.ResumableRandomAccessFileListener
import com.ning.http.client.resumable.ResumableAsyncHandler
import com.ning.http.client.resumable.ResumableIOExceptionFilter
import com.sampullara.cli.Args
import com.sampullara.cli.Argument
import org.zendesk.client.v2.Zendesk
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.URL
import java.nio.channels.Channels
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

public class FetchAttachments : AbstractCommand("fetch-attachments") {

    @Argument("id")
    var ticketId: Long? = null

    @Argument("destination")
    var destination: String? = null

    override fun run(options: Main, args: List<String>, client: Zendesk): Int {
        @Suppress("NAME_SHADOWING")
        val args = Args.parse(this, args.toTypedArray(), true)
        val ids = ArrayList<Long>()
        for (it in args) {
            try {
                ids.add(it.toLong())
            } catch(e: Exception) {
            }
        }
        if (ticketId != null) {
            ids.add(ticketId!!)
        }
        if (ids.isEmpty()) {
            return err("At least one ticket id required")
        }

        val destination = destination?.let { Paths.get(it) } ?: Paths.get(System.getProperty("user.home")!!, "Zendesk")

        for (id in ids) {
            val comments = client.getTicketComments(id)
            val attachments = comments.map { it.attachments }.flatten()

            if (attachments.isEmpty()) continue

            val dir = destination.resolve("ZD-$id")
            if (Files.notExists(dir)) {
                Files.createDirectories(dir)
            }
            for (attachment in attachments) {
                val file = dir.resolve("${attachment.id}-${attachment.fileName}")
                if (Files.exists(file)) continue

                scheduleDownload(attachment.contentUrl, file)
            }

        }
        // TODO: Migrate to async download
        return processDownloads(options)
    }

    private val downloadQueue: Queue<Pair<String, Path>> = ArrayDeque()

    private fun scheduleDownload(url: String, file: Path) {
        downloadQueue.add(url to file);
    }

    private fun processDownloads(options: Main): Int {
        if (downloadQueue.isEmpty()) {
            println("Nothing to download")
            return 0
        }
        while (downloadQueue.isNotEmpty()) {
            val pair = downloadQueue.poll() ?: break
            val website = URL(pair.first);
            print("Downloading $website into ${pair.second.toString()}...")
            val rbc = Channels.newChannel(website.openStream());
            val fos = FileOutputStream(pair.second.toFile());
            fos.channel.transferFrom(rbc, 0, Long.MAX_VALUE);
            fos.close()
            rbc.close()
            println(" Done")
        }
        return 0
    }

    private fun processDownloadsAsync(options: Main): Int {
        if (downloadQueue.isEmpty()) {
            println("Nothing to download")
            return 0
        }
        val config = AsyncHttpClientConfig.Builder()
                .addIOExceptionFilter(ResumableIOExceptionFilter())
                .setFollowRedirect(true)
                .setMaxConnectionsPerHost(2)
                .build()
        val realm = Realm.RealmBuilder()
                .setRealmName("Web Password")
                .setScheme(Realm.AuthScheme.BASIC)
                .setPrincipal(options.username + if (options.token != null) "/token")
                .setPassword(options.token ?: options.password!!)
                .setUsePreemptiveAuth(true).build();
        val client = AsyncHttpClient(config)
        val waiting = ArrayList<ListenableFuture<RandomAccessFile>>()
        val files = ArrayList<RandomAccessFile>()
        while (downloadQueue.isNotEmpty()) {
            val pair = downloadQueue.poll() ?: break
            val handler = ResumableAsyncHandler<RandomAccessFile>(false);
            val file = RandomAccessFile(pair.second.toFile(), "rw")
            val listener = ResumableRandomAccessFileListener(file)
            handler.setResumableListener(listener)
            val future = client.prepareGet(pair.first).setRealm(realm).setFollowRedirects(true).execute(handler)
            waiting.add(future)
            files.add(file)
        }


        while (true) {
            val unfinished = waiting.filterNot { it.isCancelled || it.isDone }
            val left = unfinished.size
            if (left == 0) break
            println("Waiting for $left downloads to complete...:")
            for (f in unfinished) {
                println("\n$f")
            }
            try {
                Thread.sleep(1000)
            } catch(e: InterruptedException) {
                System.err?.println("Waiting for downloads interrupted. Cancelling non finished downloads (if any)")
                for (future in waiting) {
                    if (future.isCancelled || future.isDone) continue
                    future.cancel(true)
                }
                return 1
            }
        }
        files.forEach { it.close() }
        client.close()
        println("All downloads to completed")
        return 0
    }
}
