package com.badoo.automation.deviceserver.ios.simulator.data

import com.badoo.automation.deviceserver.command.CommandResult
import com.badoo.automation.deviceserver.data.UDID
import com.badoo.automation.deviceserver.host.IRemote
import com.badoo.automation.deviceserver.util.withDefers
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.file.Paths
import java.time.Duration

class Media(
    private val remote: IRemote,
    private val udid: UDID,
    deviceSetPath: String
) {
    private val mediaPath = Paths.get(deviceSetPath, udid, "data", "Media")
    private val logger = LoggerFactory.getLogger(javaClass.simpleName)

    fun reset() {
        val removeCmd = "rm -rf $mediaPath"

        val result = remote.shell(removeCmd)

        if (!result.isSuccess) {
            throw RuntimeException("Could not reset Media: $result")
        }

        // restart assetsd to prevent fbsimctl upload failing with Error Domain=NSCocoaErrorDomain Code=-1 \"(null)\"
        restartAssetsd()
    }

    fun list() : List<String> {
        val listCmd = listOf("ls", "-1", "$mediaPath/DCIM/100APPLE")
        return remote.execIgnoringErrors(listCmd).stdOut.trim().lines()
    }

    fun addMedia(file: File, data: ByteArray) {
        withDefers(logger) {
            val tmpFile = File.createTempFile(file.nameWithoutExtension, ".${file.extension}")
            defer { tmpFile.delete() }
            tmpFile.writeBytes(data)

            val mediaPath: String = if (remote.isLocalhost()) {
                tmpFile.absolutePath
            } else {
                val remoteMediaDir = remote.execIgnoringErrors(listOf("/usr/bin/mktemp", "-d")).stdOut.trim()
                defer { remote.execIgnoringErrors(listOf("/bin/rm", "-rf", remoteMediaDir)) }
                remote.scpToRemoteHost(tmpFile.absolutePath, remoteMediaDir, Duration.ofMinutes(1))
                File(remoteMediaDir, tmpFile.name).absolutePath
            }

            val result = remote.execIgnoringErrors(listOf("/usr/bin/xcrun", "simctl", "addmedia", udid, mediaPath))

            if (!result.isSuccess) {
                throw RuntimeException("Could not add Media to device: $result")
            }
        }
    }

    private fun restartAssetsd() {
        val restartCmd = listOf(
            "xcrun", "simctl", "spawn", udid, "launchctl", "kickstart", "-k", "-p", "system/com.apple.assetsd"
        )

        val result = remote.execIgnoringErrors(restartCmd)

        if (!result.isSuccess) {
            throw RuntimeException("Could not restart assetsd service: $result")
        }
    }
}
