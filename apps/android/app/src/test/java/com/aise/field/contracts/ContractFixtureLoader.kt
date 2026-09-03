package com.aise.field.contracts

import java.io.File
import java.security.MessageDigest

object ContractFixtureLoader {
    const val PR_HEAD_SHA = "37a421f72360acada871653eddc1cb3248437b6d"

    fun getFixturesDirectory(): File {
        var dir: File? = File(".").canonicalFile
        while (dir != null && !File(dir, "packages/shared-contracts/fixtures").exists()) {
            dir = dir.parentFile
        }
        val rootDir = requireNotNull(dir) { "Repository root containing packages/shared-contracts/fixtures not found" }
        val fixturesDir = File(rootDir, "packages/shared-contracts/fixtures")
        require(fixturesDir.exists()) { "Fixtures directory not found at $fixturesDir" }
        return fixturesDir
    }

    fun readFixtureText(fixtureName: String): String {
        val file = File(getFixturesDirectory(), fixtureName)
        require(file.exists()) { "Fixture file $fixtureName does not exist at ${file.absolutePath}" }
        return file.readText(Charsets.UTF_8)
    }

    fun computeSha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(text.toByteArray(Charsets.UTF_8))
        return hash.joinToString("") { "%02x".format(it) }
    }
}
