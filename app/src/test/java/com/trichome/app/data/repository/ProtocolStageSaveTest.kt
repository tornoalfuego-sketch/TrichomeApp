package com.trichome.app.data.repository

import com.trichome.app.data.dao.ProtocolDao
import com.trichome.app.data.dao.ProtocolStageDao
import com.trichome.app.data.entity.Protocol
import com.trichome.app.data.entity.ProtocolStage
import com.trichome.app.data.model.GrowRange
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * The round trip, against a store that behaves like Room's.
 *
 * ## What this test is for
 *
 * `ProtocolRepository.replaceStages` used to `clearStages(protocolId)` and re-insert.
 * That was survivable only while nothing wrote `protocol_stages.vpdTarget`, and this
 * phase gives that column a write path — so from here on, every save of a protocol's
 * *name* would have erased the band the grower had just set, and regenerated every
 * stage's primary key. `AGENTS.md` §11 sets the data-loss budget at zero.
 *
 * The store below is a `MutableList` behind the real DAO interface, and it reproduces the
 * three Room behaviours the fix depends on: `insert` assigns an id to a row that has
 * none and REPLACES a row that does, `update` addresses a row by its id, and `delete`
 * addresses a row by its id. The tests then assert on **what is in the list afterwards**
 * and on the **sequence of statements** that got it there — which is what makes them fail
 * against the old implementation rather than merely describing the new one.
 *
 * There is no Compose or Robolectric runtime on this classpath, so the UI's contribution
 * to the round trip is asserted by reading its source:
 * [theEditorHandsBackTheStageIds] and [theWriteSurfacesAreReachable] in
 * `ProtocolTargetsStructureTest`.
 */
class ProtocolStageSaveTest {

    /* ── A Room-shaped store ─────────────────────────────────────────────── */

    /**
     * `protocol_stages` as a list, plus the log of statements that touched it.
     *
     * The log is what distinguishes "the rows ended up right" from "the rows were never
     * destroyed in the first place": a clear-and-reinsert leaves the same three rows
     * behind, so only the statements can show that every save tore the schedule down.
     */
    private class FakeStageDao : ProtocolStageDao {
        val rows = mutableListOf<ProtocolStage>()
        val log = mutableListOf<String>()
        private var nextId = 1L

        override suspend fun getStagesByProtocol(protocolId: Long): List<ProtocolStage> =
            rows.filter { it.protocolId == protocolId }.sortedBy { it.sortOrder }

        override fun watchStagesByProtocol(protocolId: Long): Flow<List<ProtocolStage>> =
            flowOf(getRows(protocolId))

        private fun getRows(protocolId: Long) =
            rows.filter { it.protocolId == protocolId }.sortedBy { it.sortOrder }

        override suspend fun insertStage(stage: ProtocolStage): Long {
            log += "insert(${stage.id})"
            if (stage.id == 0L) {
                val assigned = stage.copy(id = nextId++)
                rows += assigned
                return assigned.id
            }
            val index = rows.indexOfFirst { it.id == stage.id }
            if (index >= 0) rows[index] = stage else rows += stage
            return stage.id
        }

        override suspend fun updateStage(stage: ProtocolStage) {
            log += "update(${stage.id})"
            val index = rows.indexOfFirst { it.id == stage.id }
            if (index >= 0) rows[index] = stage
        }

        override suspend fun deleteStage(stage: ProtocolStage) {
            log += "delete(${stage.id})"
            rows.removeAll { it.id == stage.id }
        }

        override suspend fun updateStageVpdTarget(protocolId: Long, stageId: Long, band: String?) {
            log += "vpd($stageId)"
            val index = rows.indexOfFirst { it.id == stageId && it.protocolId == protocolId }
            if (index >= 0) {
                rows[index] = rows[index].copy(vpdTarget = GrowRange.decode(band))
            }
        }

        fun stored(protocolId: Long): List<ProtocolStage> =
            rows.filter { it.protocolId == protocolId }.sortedBy { it.sortOrder }
    }

    /** The protocol DAO is not on this path; touching it would mean the test drifted. */
    private class UnusedProtocolDao : ProtocolDao {
        override fun getProtocolsByPlant(plantId: Long): Flow<List<Protocol>> =
            error("the stage write path must not read the protocol list")

        override suspend fun getProtocolById(id: Long): Protocol? =
            error("the stage write path must not read the protocol row")

        override suspend fun getActiveProtocols(): List<Protocol> =
            error("the stage write path must not read the protocol row")

        override suspend fun insertProtocol(protocol: Protocol): Long =
            error("the stage write path must not write the protocol row")

        override suspend fun updateProtocol(protocol: Protocol) =
            error("the stage write path must not write the protocol row")

        override suspend fun deleteProtocol(protocol: Protocol) =
            error("the stage write path must not write the protocol row")
    }

    private lateinit var stageDao: FakeStageDao
    private lateinit var repository: ProtocolRepository

    private val protocolId = 7L

    @Before
    fun setUp() {
        stageDao = FakeStageDao()
        repository = ProtocolRepository(UnusedProtocolDao(), stageDao)
        runBlocking {
            listOf(
                ProtocolStage(
                    id = 0L,
                    protocolId = protocolId,
                    stageName = "Germinación",
                    durationDays = 7,
                    sortOrder = 0,
                    vpdTarget = GrowRange(0.4f, 0.7f)
                ),
                ProtocolStage(
                    id = 0L,
                    protocolId = protocolId,
                    stageName = "Vegetativa",
                    durationDays = 35,
                    sortOrder = 1,
                    vpdTarget = GrowRange(1.0f, 1.4f)
                ),
                ProtocolStage(
                    id = 0L,
                    protocolId = protocolId,
                    stageName = "Floración",
                    durationDays = 56,
                    sortOrder = 2
                )
            ).forEach { stageDao.insertStage(it) }
        }
        stageDao.log.clear()
    }

    private fun storedStageIds() = stageDao.stored(protocolId).map { it.id }

    private fun draft(id: Long, name: String, days: Int) = ProtocolStage(
        id = id,
        protocolId = protocolId,
        stageName = name,
        durationDays = days
    )

    /* ── The load-bearing test ────────────────────────────────────────────── */

    @Test
    fun aProtocolNameEditLeavesEveryStageIdAndEveryBandExactlyWhereTheyWere() {
        val ids = runBlocking { repository.getStages(protocolId) }.map { it.id }

        runBlocking {
            repository.saveStages(
                protocolId,
                // What the editor hands back: it knows each stage's id, its name and its
                // days. It does not know — and never could — the band.
                listOf(
                    draft(ids[0], "Germinación", 7),
                    draft(ids[1], "Vegetativa", 35),
                    draft(ids[2], "Floración", 56)
                )
            )
        }

        val stored = stageDao.stored(protocolId)
        assertEquals(
            "every stage kept its row: the schedule was edited, not rebuilt",
            ids,
            stored.map { it.id }
        )
        assertEquals(
            GrowRange(0.4f, 0.7f),
            stored[0].vpdTarget
        )
        assertEquals(
            "a band the grower set survives a protocol save",
            GrowRange(1.0f, 1.4f),
            stored[1].vpdTarget
        )
        assertEquals(
            "and the stage that never had one still declares none",
            null,
            stored[2].vpdTarget
        )
        assertEquals(
            "three statements, all updates: no row was deleted and none was re-inserted",
            listOf("update(${ids[0]})", "update(${ids[1]})", "update(${ids[2]})"),
            stageDao.log
        )
    }

    @Test
    fun aRenamedStageIsAnUpdateAndNotADeleteFollowedByTwoInserts() {
        val ids = runBlocking { repository.getStages(protocolId) }.map { it.id }

        runBlocking {
            repository.saveStages(
                protocolId,
                listOf(
                    draft(ids[0], "Imbibición", 7),
                    draft(ids[1], "Vegetativa", 35),
                    draft(ids[2], "Floración", 56)
                )
            )
        }

        assertEquals("Imbibición", stageDao.stored(protocolId)[0].stageName)
        assertEquals(GrowRange(0.4f, 0.7f), stageDao.stored(protocolId)[0].vpdTarget)
        assertEquals(listOf("update(${ids[0]})", "update(${ids[1]})", "update(${ids[2]})"), stageDao.log)
    }

    @Test
    fun aStageTheGrowerRemovedIsTheOnlyRowDeleted() {
        val ids = runBlocking { repository.getStages(protocolId) }.map { it.id }

        runBlocking {
            repository.saveStages(
                protocolId,
                listOf(
                    draft(ids[0], "Germinación", 7),
                    draft(ids[2], "Floración", 60),
                    ProtocolStage(
                        protocolId = protocolId,
                        stageName = "Lavado",
                        durationDays = 3
                    )
                )
            )
        }

        val stored = stageDao.stored(protocolId)
        assertEquals(
            listOf(ids[0], ids[2]),
            stored.map { it.id }.dropLast(1)
        )
        assertEquals("Lavado", stored.last().stageName)
        assertEquals(3, stored.size)
        assertEquals(
            "one update, one update, one insert, one delete — the removal is named",
            listOf(
                "update(${ids[0]})",
                "update(${ids[2]})",
                "insert(0)",
                "delete(${ids[1]})"
            ),
            stageDao.log
        )
        assertEquals(
            "the deleted stage was the one the grower removed",
            null,
            stored.firstOrNull { it.stageName == "Vegetativa" }
        )
    }

    @Test
    fun anAddedStageArrivesWithAnIdAndTheOthersKeepTheirs() {
        val ids = runBlocking { repository.getStages(protocolId) }.map { it.id }

        runBlocking {
            repository.saveStages(
                protocolId,
                listOf(
                    draft(ids[0], "Germinación", 7),
                    draft(ids[1], "Vegetativa", 35),
                    draft(ids[2], "Floración", 56),
                    ProtocolStage(protocolId = protocolId, stageName = "Lavado", durationDays = 3)
                )
            )
        }

        val stored = stageDao.stored(protocolId)
        assertEquals(4, stored.size)
        assertEquals(ids, stored.dropLast(1).map { it.id })
        assertTrue(
            "the added row got a fresh id rather than colliding with a kept one",
            stored.last().id !in ids
        )
    }

    @Test
    fun reorderingAProtocolsStagesDoesNotRecreateThem() {
        val ids = runBlocking { repository.getStages(protocolId) }.map { it.id }

        runBlocking {
            repository.saveStages(
                protocolId,
                listOf(
                    draft(ids[2], "Floración", 56),
                    draft(ids[0], "Germinación", 7),
                    draft(ids[1], "Vegetativa", 35)
                )
            )
        }

        assertEquals(
            listOf("Floración", "Germinación", "Vegetativa"),
            stageDao.stored(protocolId).map { it.stageName }
        )
        assertEquals(ids.toSet(), stageDao.stored(protocolId).map { it.id }.toSet())
    }

    /* ── The band has its own one-column write ────────────────────────────── */

    @Test
    fun aBandIsWrittenByItsOwnCallAndTouchesNothingElse() {
        val ids = runBlocking { repository.getStages(protocolId) }.map { it.id }

        runBlocking { repository.setStageVpdTarget(protocolId, ids[2], GrowRange(1.2f, 1.6f)) }

        val stored = stageDao.stored(protocolId)
        assertEquals(GrowRange(1.2f, 1.6f), stored[2].vpdTarget)
        assertEquals(GrowRange(0.4f, 0.7f), stored[0].vpdTarget)
        assertEquals(listOf("vpd(${ids[2]})"), stageDao.log)
        assertEquals(ids, stored.map { it.id })
    }

    @Test
    fun aBandCanBeClearedAndClearingIsAStoredNull() {
        val ids = runBlocking { repository.getStages(protocolId) }.map { it.id }

        runBlocking { repository.setStageVpdTarget(protocolId, ids[1], null) }

        assertNull(stageDao.stored(protocolId)[1].vpdTarget)
        assertEquals(
            "and the stored form is NULL rather than a text zero",
            null,
            GrowRange.encode(stageDao.stored(protocolId)[1].vpdTarget)
        )
    }

    @Test
    fun aBandRoundTripsThroughTheStoredTextForm() {
        val ids = runBlocking { repository.getStages(protocolId) }.map { it.id }
        val band = GrowRange(0.65f, 1.35f)

        runBlocking { repository.setStageVpdTarget(protocolId, ids[0], band) }

        val stored = stageDao.rows.first { it.id == ids[0] }
        assertEquals(
            "what was written is the encoded band, and what the row mapper reads back is " +
                "the band",
            band,
            GrowRange.decode(GrowRange.encode(band))
        )
        assertEquals(band, stored.vpdTarget)
    }

    @Test
    fun aBandCannotBeWrittenThroughAnotherProtocol() {
        val foreign = ProtocolStage(
            id = 0L,
            protocolId = 99L,
            stageName = "Ajena",
            durationDays = 1
        )
        runBlocking { stageDao.insertStage(foreign) }
        val foreignId = stageDao.rows.first { it.protocolId == 99L }.id

        runBlocking { repository.setStageVpdTarget(protocolId, foreignId, GrowRange(1f, 2f)) }

        assertNull(
            "the write is addressed by protocol as well as by stage",
            stageDao.rows.first { it.id == foreignId }.vpdTarget
        )
    }

    /* ── The destructive path is not merely unused ────────────────────────── */

    @Test
    fun theDaoNoLongerOffersABulkDeleteOfAProtocolsStages() {
        val dao = File("src/main/java/com/trichome/app/data/dao/AllDaos.kt")
            .takeIf { it.isFile }
            ?: File("app/src/main/java/com/trichome/app/data/dao/AllDaos.kt")
        val code = dao.readText(Charsets.UTF_8)
            .replace(Regex("""/\*[\s\S]*?\*/"""), " ")
            .replace(Regex("""//[^\n]*"""), " ")

        assertTrue("AllDaos.kt was not read, so this guard is inert", code.contains("interface ProtocolStageDao"))
        assertTrue(
            "ProtocolStageDao must not declare a bulk delete again: it is what made every " +
                "protocol save a data-loss event",
            !code.contains("clearStages")
        )
        assertTrue(
            "nor a DELETE FROM protocol_stages by protocol",
            !Regex("""DELETE\s+FROM\s+protocol_stages""").containsMatchIn(code)
        )
    }

    @Test
    fun theRepositoryNoLongerClearsAProtocolsStagesOnSave() {
        val file = File("src/main/java/com/trichome/app/data/repository/Repositories.kt")
            .takeIf { it.isFile }
            ?: File("app/src/main/java/com/trichome/app/data/repository/Repositories.kt")
        val code = file.readText(Charsets.UTF_8)
            .replace(Regex("""/\*[\s\S]*?\*/"""), " ")
            .replace(Regex("""//[^\n]*"""), " ")

        assertTrue("Repositories.kt was not read, so this guard is inert", code.contains("class ProtocolRepository"))
        assertTrue(
            "`clearStages` may not come back, and neither may the old `replaceStages` name " +
                "that carried it",
            !code.contains("clearStages") && !code.contains("replaceStages")
        )
        assertTrue(
            "the schedule save must be the reconciling one",
            code.contains("reconcileStages")
        )
    }
}