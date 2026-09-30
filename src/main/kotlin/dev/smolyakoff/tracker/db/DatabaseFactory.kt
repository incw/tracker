package dev.smolyakoff.tracker.db

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import org.sqlite.SQLiteConfig
import java.io.File

object DatabaseFactory {
    private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)

    fun init(dbPath: String) {
        val dbFile = File(dbPath)
        dbFile.parentFile?.mkdirs()

        logger.info("Initializing SQLite database at: {}", dbFile.absolutePath)

        val config = SQLiteConfig().apply {
            setJournalMode(SQLiteConfig.JournalMode.WAL)
            setBusyTimeout(5000)
            enforceForeignKeys(true)
            setSynchronous(SQLiteConfig.SynchronousMode.NORMAL)
        }

        val url = "jdbc:sqlite:$dbPath"

        Database.connect(
            url = url,
            driver = "org.sqlite.JDBC",
            setupConnection = { connection ->
                config.apply(connection)
            }
        )

        transaction {
            SchemaUtils.createMissingTablesAndColumns(
                TrackedPlayersTable,
                ChatSubscriptionsTable,
                MatchRecordsTable,
                EloSnapshotsTable,
                ChatTrackedPlayersTable,
                WordReactionsTable,
                ChatSettingsTable
            )
        }
        logger.info("Database schema initialized successfully.")
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
