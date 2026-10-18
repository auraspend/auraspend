package com.awbuilds.auraspend

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.awbuilds.auraspend.data.classification.defaultCategories
import com.awbuilds.auraspend.data.local.AppDatabase
import com.awbuilds.auraspend.data.local.entities.BudgetEntity
import com.awbuilds.auraspend.data.local.entities.CategoryEntity
import com.awbuilds.auraspend.data.local.entities.SavingsGoalEntity
import com.awbuilds.auraspend.data.local.entities.SmsMessageEntity
import com.awbuilds.auraspend.data.local.entities.SubscriptionEntity
import com.awbuilds.auraspend.data.local.entities.TransactionEntity
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import org.junit.Assume
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Seeds the **real** app database with demo data, for UI review, screenshots and
 * manual QA on a device.
 *
 * This is not an assertion test — it has no expectations. It writes to the app's
 * actual `auraspend_db`, so the data survives after the test APK is uninstalled
 * and the app can be launched and driven normally:
 *
 * ```
 * ./gradlew :app:connectedDevDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.awbuilds.auraspend.DemoDataSeeder
 * adb shell am start -n com.awbuilds.auraspend.dev/com.awbuilds.auraspend.MainActivity
 * ```
 *
 * To clear it: `adb shell pm clear com.awbuilds.auraspend`.
 *
 * ## Why the numbers are chosen, not arbitrary
 *
 * The data describes a **healthy month** — income above spending, every budget
 * under its limit — because it is also what the Play screenshots are shot from;
 * an ad that shows "over budget" and a negative balance sells the opposite of
 * the product. It still stresses the things a finance UI gets wrong:
 *
 * - **Lakh/crore grouping.** A ₹1,25,000 salary exists specifically so
 *   `formatMoney`'s hand-rolled Indian grouping is visible. `NumberFormat` gets
 *   this wrong on `en_IN`, which is the whole reason that formatter is
 *   hand-written.
 * - **Long content.** "South Indian Expressways Authority" and
 *   "Reliance Digital Store — Brigade Road" are wider than most merchant names,
 *   to show ellipsis and wrapping rather than assuming short labels.
 * - **Every one of the 12 default categories** has at least one transaction, so
 *   the category breakdown and donut chart are fully populated.
 * - **Budgets all under, one at 93 %**, so the near-limit rendering is still
 *   exercised. The over-limit paths are covered by unit tests rather than by
 *   data that would look alarming in a screenshot.
 * - **Mix of income and expense**, with a mid-month salary so the dashboard's
 *   balance figure is not simply "all expenses".
 */
@RunWith(AndroidJUnit4::class)
class DemoDataSeeder {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun seed() = runBlocking {
        // Opt-in only. This wipes the app's database and writes demo rows, so a
        // routine instrumented run must not do that to whatever is on the
        // device — a full-suite run on a real phone destroyed the data under
        // test. It stays invokable on purpose:
        //   am instrument -w -e class com.awbuilds.auraspend.DemoDataSeeder \
        //       -e seedDemoData true com.awbuilds.auraspend.dev.test/androidx.test.runner.AndroidJUnitRunner
        Assume.assumeTrue(
            "pass -e seedDemoData true to wipe and seed demo data",
            InstrumentationRegistry.getArguments().getString("seedDemoData") == "true"
        )

        // The real application, so this seeder writes into the same encrypted
        // database the app itself uses.
        val db = (context.applicationContext as AuraSpendApp).database

        db.clearAllTables()

        db.categoryDao().insertCategories(
            defaultCategories.map {
                CategoryEntity(
                    id = it.id, name = it.name, icon = it.icon,
                    color = it.color, isDefault = it.isDefault
                )
            }
        )

        val now = System.currentTimeMillis()
        fun daysAgo(days: Long) = now - TimeUnit.DAYS.toMillis(days)
        fun inDays(days: Long) = now + TimeUnit.DAYS.toMillis(days)

        val tx = mutableListOf<TransactionEntity>()
        fun expense(
            id: String, amount: Double, category: String, note: String,
            merchant: String, daysAgo: Long, recurring: Boolean = false,
            frequency: String? = null, subscription: String? = null
        ) {
            tx += TransactionEntity(
                id = id, amount = amount, categoryId = category, note = note,
                merchant = merchant, bankName = "HDFC Bank",
                dateTimestamp = daysAgo(daysAgo), type = "EXPENSE",
                isRecurring = recurring, recurrenceFrequency = frequency,
                nextDueDateTimestamp = if (recurring) inDays(28) else null,
                subscriptionName = subscription
            )
        }

        // Income — lakh-scale, to exercise Indian digit grouping.
        tx += TransactionEntity(
            id = "tx-salary-1", amount = 1_25_000.0, categoryId = "cat_salary",
            note = "Monthly salary", merchant = "ACME Corp Payroll",
            bankName = "HDFC Bank", dateTimestamp = daysAgo(24), type = "INCOME"
        )
        tx += TransactionEntity(
            id = "tx-salary-2", amount = 1_18_400.0, categoryId = "cat_salary",
            note = "Monthly salary", merchant = "ACME Corp Payroll",
            bankName = "HDFC Bank", dateTimestamp = daysAgo(54), type = "INCOME"
        )
        tx += TransactionEntity(
            id = "tx-freelance", amount = 72_500.0, categoryId = "cat_salary",
            note = "Consulting invoice", merchant = "Razorpay Payments",
            bankName = "HDFC Bank", dateTimestamp = daysAgo(11), type = "INCOME"
        )

        // Food & Dining
        expense("tx-f1", 640.0, "cat_food", "Dinner", "Swiggy", 0)
        expense("tx-f2", 1280.0, "cat_food", "Weekend brunch", "Third Wave Coffee", 1)
        expense("tx-f3", 249.0, "cat_food", "Lunch", "Chaayos", 2)
        expense("tx-f4", 1899.0, "cat_food", "Family dinner", "Barbeque Nation", 5)
        expense("tx-f5", 540.0, "cat_food", "Pizza", "Dominos", 7)
        expense("tx-f6", 320.0, "cat_food", "Takeaway", "Zomato", 9)

        // Grocery — long product/merchant names on purpose.
        expense("tx-g1", 4250.0, "cat_grocery", "Monthly groceries", "BigBasket", 3)
        expense("tx-g2", 899.0, "cat_grocery", "Vegetables and milk", "Zepto", 6)
        expense("tx-g3", 2150.0, "cat_grocery", "Weekly restock", "DMart", 12)

        // Transport
        expense("tx-t1", 245.0, "cat_transport", "Ride home", "Uber", 0)
        expense("tx-t2", 1800.0, "cat_transport", "Fuel", "Indian Oil", 4)
        expense("tx-t3", 750.0, "cat_transport", "Metro card top-up", "Namma Metro", 8)

        // Shopping — deliberately long merchant strings.
        expense("tx-s1", 12_499.0, "cat_shopping", "Headphones", "Reliance Digital Store — Brigade Road", 2)
        expense("tx-s2", 2_399.0, "cat_shopping", "Running shoes", "Decathlon", 10)
        expense("tx-s3", 1_899.0, "cat_shopping", "Winter jacket", "Westside", 16)

        // Bills & Utilities. The rent is deliberately ordinary: this dataset is
        // what the Play screenshots are shot from, so nothing here is allowed to
        // produce a negative balance or an over-budget row. The lakh-scale
        // grouping case is carried by the salary (income) instead.
        expense("tx-b1", 42_000.0, "cat_bills", "Rent", "South Indian Expressways Authority", 25)
        expense("tx-b2", 2_180.0, "cat_bills", "Electricity", "BESCOM", 5)
        expense("tx-b3", 1_299.0, "cat_bills", "Broadband", "ACT Fibernet", 6)
        expense("tx-b4", 799.0, "cat_bills", "Mobile postpaid", "Airtel", 7)

        // Entertainment
        expense("tx-e1", 649.0, "cat_entertainment", "Subscription", "Netflix", 3, true, "MONTHLY", "Netflix")
        expense("tx-e2", 149.0, "cat_entertainment", "Music", "Spotify", 6, true, "MONTHLY", "Spotify")
        expense("tx-e3", 850.0, "cat_entertainment", "Concert tickets", "BookMyShow", 9)
        expense("tx-e4", 649.0, "cat_entertainment", "Subscription", "Netflix", 33, true, "MONTHLY", "Netflix")

        // Healthcare
        expense("tx-h1", 1_450.0, "cat_healthcare", "Dentist visit", "Practo", 8)
        expense("tx-h2", 320.0, "cat_healthcare", "Pharmacy", "Apollo Pharmacy", 14)

        // Education
        expense("tx-ed1", 4_999.0, "cat_education", "Course", "Coursera", 13)

        // Subscriptions (as transactions, so the count is non-zero)
        expense("tx-sub1", 149.0, "cat_subscription", "Music", "Google One", 4, true, "MONTHLY", "Google One")

        // Transfer
        expense("tx-tr1", 15_000.0, "cat_transfer", "To savings", "UPI transfer", 2)
        expense("tx-tr2", 5_000.0, "cat_transfer", "Shared cab", "UPI transfer", 6)

        db.transactionDao().insertTransactions(tx)

        // Budgets: all under the limit on purpose. This data is also what the
        // store screenshots show, and an ad that says "over budget" sells the
        // opposite of the product. Shopping sits at 93 % so the near-limit
        // rendering is still exercised; the over-limit paths are covered by
        // unit tests, not by demo data.
        db.budgetDao().insertBudget(
            BudgetEntity("bg-food", "cat_food", 8_000.0, 0.0, "MONTHLY")
        )
        db.budgetDao().insertBudget(
            BudgetEntity("bg-grocery", "cat_grocery", 9_000.0, 0.0, "MONTHLY")
        )
        db.budgetDao().insertBudget(
            BudgetEntity("bg-transport", "cat_transport", 5_000.0, 0.0, "MONTHLY")
        )
        db.budgetDao().insertBudget(
            BudgetEntity("bg-shopping", "cat_shopping", 18_000.0, 0.0, "MONTHLY")
        )
        db.budgetDao().insertBudget(
            BudgetEntity("bg-entertainment", "cat_entertainment", 4_000.0, 0.0, "MONTHLY")
        )

        db.subscriptionDao().insertSubscription(
            SubscriptionEntity("sub-netflix", "Netflix", 649.0, "cat_entertainment", "MONTHLY", inDays(21), true)
        )
        db.subscriptionDao().insertSubscription(
            SubscriptionEntity("sub-spotify", "Spotify Premium", 149.0, "cat_entertainment", "MONTHLY", inDays(9), true)
        )
        db.subscriptionDao().insertSubscription(
            SubscriptionEntity("sub-google", "Google One", 149.0, "cat_subscription", "MONTHLY", inDays(26), true)
        )
        db.subscriptionDao().insertSubscription(
            SubscriptionEntity("sub-icloud", "iCloud+ 2TB", 1_299.0, "cat_subscription", "MONTHLY", inDays(3), true)
        )
        db.subscriptionDao().insertSubscription(
            SubscriptionEntity("sub-office", "Microsoft 365 Family", 1_499.0, "cat_subscription", "YEARLY", inDays(180), true)
        )
        db.subscriptionDao().insertSubscription(
            SubscriptionEntity("sub-prime", "Amazon Prime", 299.0, "cat_entertainment", "MONTHLY", inDays(14), false)
        )

        db.savingsGoalDao().insertSavingsGoal(
            SavingsGoalEntity("goal-emergency", "Emergency fund", 3_00_000.0, 1_85_000.0, inDays(420))
        )
        db.savingsGoalDao().insertSavingsGoal(
            SavingsGoalEntity("goal-japan", "Japan trip", 1_50_000.0, 62_500.0, inDays(300))
        )
        db.savingsGoalDao().insertSavingsGoal(
            SavingsGoalEntity("goal-laptop", "New laptop", 2_00_000.0, 2_00_000.0, null)
        )
        db.savingsGoalDao().insertSavingsGoal(
            SavingsGoalEntity("goal-newbike", "New bike", 1_25_000.0, 18_000.0, inDays(540))
        )

        // SMS history, in a spread of states so the classification screens are populated.
        db.smsMessageDao().insertAll(
            listOf(
                SmsMessageEntity(
                    id = "sms-1", address = "AXISBK",
                    body = "Rs. 640.00 debited via UPI at SWIGGY on 29-SEP-26. UPI ref 8841203.",
                    receivedAt = daysAgo(0), status = "SAVED", amount = 640.0,
                    type = "EXPENSE", merchant = "SWIGGY", categoryId = "cat_food",
                    confidence = 0.94f, attempts = 1, updatedAt = now
                ),
                SmsMessageEntity(
                    id = "sms-2", address = "SBIINB",
                    body = "Rs 1,25,000.00 credited to your account on 28-09-26 towards salary. Avl bal Rs 4,52,310.55.",
                    receivedAt = daysAgo(1), status = "SAVED", amount = 1_25_000.0,
                    type = "INCOME", merchant = null, categoryId = "cat_salary",
                    confidence = 0.91f, attempts = 1, updatedAt = now
                ),
                SmsMessageEntity(
                    id = "sms-3", address = "HDFCBK",
                    body = "Rs. 649.00 debised at NETFLIX.COM on 27-09-26. Card ending 4421.",
                    receivedAt = daysAgo(2), status = "NEW", attempts = 0
                ),
                SmsMessageEntity(
                    id = "sms-4", address = "CANARA",
                    body = "Rs 4250.00 spent at BIGBASKET.COM on 26-09-26. Dial 1930 to report cyber fraud.",
                    receivedAt = daysAgo(3), status = "PROCESSED", amount = 4250.0,
                    type = "EXPENSE", merchant = "BIGBASKET", categoryId = "cat_grocery",
                    confidence = 0.88f, attempts = 1, updatedAt = now
                ),
                SmsMessageEntity(
                    id = "sms-5", address = "UNKNOWN",
                    body = "Dear customer, your KYC is pending. Please visit your nearest branch.",
                    receivedAt = daysAgo(4), status = "SKIPPED", attempts = 1, updatedAt = now
                ),
                SmsMessageEntity(
                    id = "sms-6", address = "PAYTM",
                    body = "Rs 12999.00 paid to Reliance Digital Store on 25-09-26. UPI ref 5512033.",
                    receivedAt = daysAgo(5), status = "SAVED", amount = 12_499.0,
                    type = "EXPENSE", merchant = "RELIANCE DIGITAL", categoryId = "cat_shopping",
                    confidence = 0.89f, attempts = 1, updatedAt = now
                )
            )
        )

        // Deliberately no `db.close()`: this is the *application's* singleton
        // database, opened for the life of the process. Closing it here left
        // every later test in the same instrumented run failing with
        // "Database is closed" — a per-class run never noticed, a full-suite run
        // on a real phone did.
    }
}
