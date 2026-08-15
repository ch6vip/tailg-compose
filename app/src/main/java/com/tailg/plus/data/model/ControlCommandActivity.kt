package com.tailg.plus.data.model

/**
 * Port of `lib/models/control_command_activity.dart`.
 *
 * `ControlCommandActivityLog` keeps one row per command and replaces a pending
 * state with a terminal one. The Dart `assert`s are debug-only; Kotlin
 * `require` throws unconditionally — a documented fail-fast deviation.
 */
enum class ControlCommandActivityStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
}

data class ControlCommandActivity(
    val id: Int,
    val command: CommandCode,
    val title: String,
    val subtitle: String,
    val status: ControlCommandActivityStatus,
) {
    fun copyWith(
        title: String? = null,
        subtitle: String? = null,
        status: ControlCommandActivityStatus? = null,
    ): ControlCommandActivity = ControlCommandActivity(
        id = id,
        command = command,
        title = title ?: this.title,
        subtitle = subtitle ?: this.subtitle,
        status = status ?: this.status,
    )
}

/** Keeps one row per command and replaces its pending state with a terminal one. */
class ControlCommandActivityLog(maxEntries: Int = 4) {

    init {
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    val maxEntries: Int = maxEntries
    private val entriesList = mutableListOf<ControlCommandActivity>()
    private var nextId = 1

    /** Read-only snapshot; Dart returns an unmodifiable view, Kotlin returns a copy. */
    val entries: List<ControlCommandActivity> get() = entriesList.toList()

    fun start(
        command: CommandCode,
        title: String,
        subtitle: String,
    ): Int {
        val id = nextId++
        entriesList.add(
            0,
            ControlCommandActivity(
                id = id,
                command = command,
                title = title,
                subtitle = subtitle,
                status = ControlCommandActivityStatus.PENDING,
            ),
        )
        if (entriesList.size > maxEntries) {
            entriesList.subList(maxEntries, entriesList.size).clear()
        }
        return id
    }

    fun finish(
        id: Int,
        title: String,
        subtitle: String,
        status: ControlCommandActivityStatus,
    ): Boolean {
        require(status != ControlCommandActivityStatus.PENDING) { "finish requires a terminal status" }
        val index = entriesList.indexOfFirst { it.id == id }
        if (index < 0) return false
        entriesList[index] = entriesList[index].copyWith(
            title = title,
            subtitle = subtitle,
            status = status,
        )
        return true
    }
}
