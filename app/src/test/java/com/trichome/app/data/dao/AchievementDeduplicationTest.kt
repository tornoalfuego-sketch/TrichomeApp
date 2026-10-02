package com.trichome.app.data.dao

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The guarantee that `Achievement.name` is a badge's identity.
 *
 * ## The defect
 *
 * The Séquito badge paid **four times** on one device: +600 XP, four rows, one
 * unlock. Two guards existed and neither was enough.
 *
 * `insertAchievement` used `@Insert(onConflict = OnConflictStrategy.REPLACE)`.
 * `REPLACE` resolves a conflict against a primary key or a unique index. The
 * `Achievement` entity has an autoincrementing `id` and **no unique index on
 * `name`**, so an incoming row never conflicted with anything and `REPLACE`
 * silently degraded into a plain insert. Four claims, four rows.
 *
 * The ViewModel de-duplicated on an in-memory snapshot read at load, and
 * `appViewModel` scopes to the navigation entry, so **two live
 * `TerpenesViewModel`s could each hold a snapshot taken before the other
 * wrote**. A read-then-write guard is not a uniqueness guarantee; it is a
 * hopeful version of one.
 *
 * ## Why this test reads source text
 *
 * Room's `@Query` and `@Insert` are **`RetentionPolicy.CLASS`**, so they are not
 * visible through `Method.getAnnotation`: a reflection-based version cannot read
 * the SQL at all. And Kotlin emits `arg0, arg1, …` rather than parameter names
 * without `-parameters`, so the named parameters are unreachable too.
 *
 * A test written that way does not fail loudly — it passes **vacuously**, having
 * found no annotation to inspect. A vacuous test is worse than no test, because
 * it reads as coverage. `WindowInsetsContractTest` already scans source text here
 * and is the established pattern for something the JVM reflection model misses.
 *
 * ## Scope, deliberately
 *
 * These are **block-level** assertions over the `AchievementDao` declaration, not
 * per-method slices. Slicing per method was tried and abandoned: a Room
 * annotation sits above the function name, so a naive slice returns a signature
 * with no annotation in it, and the test then fails for the wrong reason. A
 * block-level assertion cannot be fooled that way, and each one below still fails
 * on the specific regression it names.
 *
 * ## What this cannot prove
 *
 * It cannot execute the statement. Verifying that `WHERE NOT EXISTS` collapses
 * duplicate rows needs a real SQLite engine, and this project has none available
 * to JVM unit tests: `room-testing` is deliberately not a dependency, and
 * `androidx.sqlite:sqlite-framework` delegates to `android.database.*`, which is
 * an unmocked stub under `testDebugUnitTest`.
 *
 * The behaviour was confirmed on the device instead: four rows and 1220 XP before
 * the fix, one row and one payment after. That evidence is real, is not
 * reproducible here, and is not claimed to be.
 */
class AchievementDeduplicationTest {

    private val daoSource: String = run {
        val file = File("src/main/java/com/trichome/app/data/dao/AllDaos.kt")
        assertTrue("${file.absolutePath} not found", file.isFile)
        // Comments are stripped because this repo's source-scanning tests have
        // broken twice on prose that merely *described* the pattern they forbid.
        file.readText().stripKotlinComments()
    }

    private val achievementDao: String = run {
        val start = daoSource.indexOf("interface AchievementDao")
        assertTrue("AchievementDao not found in the DAO source", start >= 0)
        val end = daoSource.indexOf("\n}", start)
        assertTrue("the AchievementDao block is not terminated", end > start)
        daoSource.substring(start, end)
    }

    @Test
    fun theAchievementsDaoDeclaresNoConflictStrategyAnywhere() {
        // The shipped bug, as an absence over the whole block. `REPLACE` without a
        // unique index on the column looks like a safety net: the author believes
        // it de-duplicates, nothing warns, and every row appends.
        val replaceUsages = Regex("""onConflict\s*=\s*OnConflictStrategy\.REPLACE""")
            .findAll(achievementDao)
            .map { it.value }
            .toList()

        assertTrue(
            "AchievementDao uses a REPLACE conflict strategy. It resolves conflicts on a " +
                "primary key or unique index; this table has neither on `name`, so it " +
                "appends instead of replacing. Found: $replaceUsages",
            replaceUsages.isEmpty()
        )
        assertFalse(
            "and no statement may spell the same idea out as OR REPLACE.\nGot:\n$achievementDao",
            Regex("\\bOR\\s+REPLACE\\b", RegexOption.IGNORE_CASE).containsMatchIn(achievementDao)
        )
    }

    @Test
    fun theDedupInsertIsAConditionalInsertGuardedByNotExists() {
        // NOT EXISTS is not expressible as an @Insert, so the guarantee has to live
        // in a statement SQLite evaluates at write time. That is what makes it hold
        // for two coroutines which both passed an in-memory check.
        assertTrue(
            "the dedup insert must be a @Query: NOT EXISTS cannot be an @Insert.\n" +
                "Got:\n$achievementDao",
            Regex("@Query").containsMatchIn(achievementDao)
        )
        assertTrue(
            "the statement must guard on NOT EXISTS.\nGot:\n$achievementDao",
            Regex("NOT\\s+EXISTS", RegexOption.IGNORE_CASE).containsMatchIn(achievementDao)
        )
        assertTrue(
            "it must write into `achievements`.\nGot:\n$achievementDao",
            Regex("INSERT\\s+INTO\\s+achievements", RegexOption.IGNORE_CASE)
                .containsMatchIn(achievementDao)
        )
        assertTrue(
            "the guard must read the same table it writes, or it guards nothing.\n" +
                "Got:\n$achievementDao",
            Regex("FROM\\s+achievements", RegexOption.IGNORE_CASE).containsMatchIn(achievementDao)
        )
        assertEquals(
            "there must be exactly one insert into achievements. A second one without " +
                "the guard is the bug back.\nGot:\n$achievementDao",
            1,
            Regex("INSERT\\s+INTO\\s+achievements", RegexOption.IGNORE_CASE)
                .findAll(achievementDao)
                .count()
        )
    }

    @Test
    fun theDedupInsertNeverKeysOnTheAutoincrementingId() {
        // `id` is autoincrementing, so it is always new and can never express "same
        // badge". Keying the guard on it would look harmless while making it a
        // permanent no-op — the original bug wearing a different hat.
        val insert = Regex(
            "INSERT\\s+INTO\\s+achievements.*?NOT\\s+EXISTS.*?name\\s*=\\s*:name",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(achievementDao)?.value

        assertTrue("could not locate the dedup insert.\nGot:\n$achievementDao", insert != null)
        assertFalse(
            "the dedup insert must not reference `id`.\nGot:\n$insert",
            Regex("\\bid\\b", RegexOption.IGNORE_CASE).containsMatchIn(insert!!)
        )
        assertTrue(
            "it must key on `name`, which is the badge's identity.\nGot:\n$insert",
            Regex("name\\s*=\\s*:name", RegexOption.IGNORE_CASE).containsMatchIn(insert)
        )
    }

    @Test
    fun theRepairKeepsTheOldestRowOfEachBadgeAndIsScopedToOneName() {
        // The repair runs on the user's real history, so "keep the original" is part
        // of the contract: MIN(id) preserves the row whose description was written
        // when the badge was first granted. And it must never be pointed at the
        // whole table.
        assertTrue(
            "the repair must keep MIN(id) so the original row survives.\nGot:\n$achievementDao",
            Regex("MIN\\s*\\(\\s*id\\s*\\)", RegexOption.IGNORE_CASE).containsMatchIn(achievementDao)
        )
        assertTrue(
            "and it must exclude everything but that minimum.\nGot:\n$achievementDao",
            Regex("NOT\\s+IN", RegexOption.IGNORE_CASE).containsMatchIn(achievementDao)
        )

        val repair = Regex(
            "DELETE\\s+FROM\\s+achievements.*?name\\s*=\\s*:name",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        ).find(achievementDao)?.value

        assertTrue("could not locate the repair statement.\nGot:\n$achievementDao", repair != null)
        assertTrue(
            "the repair must be scoped to one badge name, never the whole table.\nGot:\n$repair",
            Regex("name\\s*=\\s*:name", RegexOption.IGNORE_CASE).containsMatchIn(repair!!)
        )
    }

    @Test
    fun theEntityHasNoUniqueIndexOnNameThatWouldHaveCaughtThis() {
        // Explains why the bug was invisible: the table has an autoincrementing id
        // and no unique index on the column the code de-duplicated by. If a future
        // migration adds one, this test is the prompt to revisit whether the
        // statement-level guard is still the right shape.
        val entity = File("src/main/java/com/trichome/app/data/entity/Extras.kt")
            .readText()
            .substringAfter("@Entity(tableName = \"achievements\")")
            .substringBefore("@Entity")

        assertFalse(
            "achievements now declares indices; re-check whether REPLACE or the " +
                "statement guard is the better mechanism.\nGot:\n$entity",
            Regex("indices\\s*=", RegexOption.IGNORE_CASE).containsMatchIn(entity)
        )
        assertEquals(
            "the entity must still have exactly the shipped fields, so the DAO's insert " +
                "parameters match one-to-one. Got:\n$entity",
            listOf("id", "name", "description", "icon", "xpReward", "isUnlocked"),
            Regex("val\\s+(\\w+)\\s*:").findAll(entity).map { it.groupValues[1] }.toList()
        )
    }
}

/**
 * Removes `//` line comments and slash-star block comments.
 *
 * A naive stripper eats the rest of a line whenever the line holds a string
 * literal with a quote, so this one only strips a trailing `//` when the rest of
 * that line has no double quote — conservative in the safe direction: it may
 * leave a comment in place, which can only cost a false positive the assertion
 * message explains, rather than silently hide code from the scanner.
 */
internal fun String.stripKotlinComments(): String {
    val out = StringBuilder(length)
    var i = 0
    var inBlock = false
    while (i < length) {
        val c = this[i]
        val next = if (i + 1 < length) this[i + 1] else ' '
        when {
            inBlock && c == '*' && next == '/' -> {
                inBlock = false
                i += 2
            }
            inBlock -> i++
            !inBlock && c == '/' && next == '*' -> {
                inBlock = true
                i += 2
            }
            !inBlock && c == '/' && next == '/' && !restOfLine(i).contains('"') -> {
                val nl = indexOf('\n', i)
                i = if (nl < 0) length else nl
            }
            else -> {
                out.append(c)
                i++
            }
        }
    }
    return out.toString()
}

private fun String.restOfLine(from: Int): String {
    val nl = indexOf('\n', from)
    return substring(from, if (nl < 0) length else nl)
}