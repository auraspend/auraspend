package com.awbuilds.auraspend

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.awbuilds.auraspend.data.classification.AutoClassificationWorker
import com.awbuilds.auraspend.data.ai.LocalLlmProvider
import com.awbuilds.auraspend.data.security.DbPassphrase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import com.awbuilds.auraspend.data.ai.ModelDownloadManager
import com.awbuilds.auraspend.data.classification.BackgroundClassificationCoordinator
import com.awbuilds.auraspend.data.classification.SmsProgressNotifier
import com.awbuilds.auraspend.data.classification.ClassificationMemory
import com.awbuilds.auraspend.data.privacy.PrivacyBackfill
import com.awbuilds.auraspend.data.classification.defaultCategories
import com.awbuilds.auraspend.data.classification.missingDefaultCategories
import com.awbuilds.auraspend.data.classification.MerchantRepository
import com.awbuilds.auraspend.data.local.AppDatabase
import com.awbuilds.auraspend.data.local.BackupCreateManager
import com.awbuilds.auraspend.data.local.BackupRestoreManager
import com.awbuilds.auraspend.data.local.UnrecognizedSmsRepository
import com.awbuilds.auraspend.data.local.entities.CategoryEntity
import com.awbuilds.auraspend.data.remote.DriveSyncManager
import com.awbuilds.auraspend.data.repository.TransactionRepositoryImpl
import com.awbuilds.auraspend.domain.repository.TransactionRepository
import com.awbuilds.auraspend.domain.usecase.ClassifyMessageUseCase
import com.awbuilds.auraspend.domain.usecase.SaveTransactionUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * The application object.
 *
 * `open` so JVM tests can build a variant that satisfies WorkManager's on-demand
 * initialization (see [RobolectricWorkManagerApp]); production never subclasses it.
 */
open class AuraSpendApp : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var transactionRepository: TransactionRepository
        private set

    lateinit var classifyMessageUseCase: ClassifyMessageUseCase
        private set

    lateinit var saveTransactionUseCase: SaveTransactionUseCase
        private set

    lateinit var driveSyncManager: DriveSyncManager
        private set

    /** Applies a Drive backup atomically (see [BackupRestoreManager]). */
    lateinit var backupRestoreManager: BackupRestoreManager
        private set

    /** Builds the JSON payload a Drive backup uploads (see [BackupCreateManager]). */
    lateinit var backupCreateManager: BackupCreateManager
        private set

    /** Shared learned-classification store (merchant -> category) used by the AI pipeline. */
    lateinit var classificationMemory: ClassificationMemory
        private set

    /** Bank messages no parser could read, surfaced rather than silently dropped. */
    lateinit var unrecognizedSms: UnrecognizedSmsRepository
        private set

    /**
     * Background categorisation status, as one ongoing notification plus one summary.
     *
     * Process-wide on purpose: the WorkManager pass and the in-process coordinator both report
     * through it, and the accumulated "drain so far" totals have to live somewhere both can
     * reach. Built with the Application context, so it holds no Activity.
     */
    lateinit var smsProgressNotifier: SmsProgressNotifier
        private set

    private val applicationScope = CoroutineScope(Dispatchers.IO)

    /**
     * How the encrypted database is opened. Both halves are `open` only so a
     * JVM test can substitute them: the AndroidKeyStore provider and
     * `libsqlcipher.so` do not exist on the test host. The alternative — a
     * silent "encryption unavailable, carry on in plaintext" fallback — is the
     * one thing this layer must never do, which is why the choice is explicit
     * at every call site. `SqlCipherDeviceTest` covers the encrypted paths on a
     * real device.
     */
    internal open val databasePassphrase: ByteArray get() = DbPassphrase.get()

    internal open val databaseOpenHelperFactory: SupportSQLiteOpenHelper.Factory?
        get() = SupportOpenHelperFactory(databasePassphrase)

    override fun onCreate() {
        super.onCreate()

        database = AppDatabase.getInstance(this, databasePassphrase, databaseOpenHelperFactory)

        transactionRepository = TransactionRepositoryImpl(
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            budgetDao = database.budgetDao(),
            subscriptionDao = database.subscriptionDao(),
            savingsGoalDao = database.savingsGoalDao()
        )

        classifyMessageUseCase = ClassifyMessageUseCase()
        saveTransactionUseCase = SaveTransactionUseCase(transactionRepository)

        classificationMemory = ClassificationMemory(database.classificationMemoryDao())
        unrecognizedSms = UnrecognizedSmsRepository(database.unrecognizedSmsDao())
        smsProgressNotifier = SmsProgressNotifier(this)

        driveSyncManager = DriveSyncManager(this)
        backupRestoreManager = BackupRestoreManager(database)
        backupCreateManager = BackupCreateManager(database)

        LocalLlmProvider.init(this)

        // Startup performance: nothing below blocks the first frame. The model
        // status sync, worker scheduling, merchant CSV parsing and seeding all
        // run on IO; the UI reads defaults until they settle.
        applicationScope.launch {
            ModelDownloadManager.sync(this@AuraSpendApp)
            AutoClassificationWorker.schedule(this@AuraSpendApp)
        }

        // Startup performance: the merchant CSV (~assets I/O + parsing) never blocks the
        // first frame. Keyword classification works without it; the merchant-database
        // upgrade lands within milliseconds on a background thread.
        applicationScope.launch {
            MerchantRepository.initialize(this@AuraSpendApp)
        }
        applicationScope.launch {
            seedDefaultCategories()
        }

        // Background auto-classification: while the app's process lives — foreground or
        // background — keep classifying pending queue rows one-by-one. `start()` is idempotent,
        // so both lifecycle transitions can call it; the coordinator no longer stops when an
        // activity becomes visible (the user asked to watch the categorisation happen), it just
        // stops posting notifications while the app is on screen. Pacing is unchanged
        // (background thread priority, a pause between messages) so a long batch stays polite.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                BackgroundClassificationCoordinator.start(this@AuraSpendApp)
                // The Classification screen shows the same queue live, so the shade copy of
                // that status is redundant while the app is on screen.
                smsProgressNotifier.cancel()
            }

            override fun onStop(owner: LifecycleOwner) {
                BackgroundClassificationCoordinator.start(this@AuraSpendApp)
            }
        })

        // One-time PII scrub of rows written before SensitiveDataMasker existed.
        applicationScope.launch {
            PrivacyBackfill.runIfNeeded(this@AuraSpendApp)
        }
    }

    /** Seeds every default category the table does not already carry (fresh installs and older installs alike). */
    suspend fun seedDefaultCategories() {
        val existingIds = database.categoryDao().getAllCategories().first().map { it.id }.toSet()
        missingDefaultCategories(existingIds).forEach { category ->
            database.categoryDao().insertCategory(
                CategoryEntity(
                    id = category.id,
                    name = category.name,
                    icon = category.icon,
                    color = category.color,
                    isDefault = category.isDefault
                )
            )
        }
    }
}
