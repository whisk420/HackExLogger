package com.whisk.hackexlogger

data class ConsoleBundle(
    val format: String = "target-intelligence-console",
    val version: Int = 1,
    val exportedAt: String? = null,
    val targets: List<TargetRecord>? = null,
    val ingests: List<ConsoleIngest>? = null
)

data class ConsoleIngest(
    val type: String? = null,
    val raw: String? = null,
    val importedAt: String? = null,
    val id: Long? = null
)

data class ImportResult(
    val targetCount: Int,
    val ingestCount: Int
)
