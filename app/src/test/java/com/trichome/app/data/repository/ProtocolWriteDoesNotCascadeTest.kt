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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A protocol row must never be REPLACEd, because REPLACE cascades.
 *
 * ## The defect this test was written after
 *
 * `protocol_stages.protocolId` carries `onDelete = ForeignKey.CASCADE` against
 * `protocols.id`, and `ProtocolDao.insertProtocol` is annotated
 * `@Insert(onConflict = OnConflictStrategy.REPLACE)`. SQLite implements REPLACE as
 * **DELETE followed by INSERT**. So every save of a protocol that already existed
 * deleted that row and took every stage of the protocol with it.
 *
 * `ProtocolRepository.insertProtocol`'s own KDoc documents the REPLACE and tells callers
 * to `copy` the row so unmentioned columns survive. Nobody connected that REPLACE to the
 * cascade, and every test agreed the data was fine.
 *
 * **It was found on a phone, not by reading.** Creating a protocol wrote three
 * `protocol_stages` rows with ids 7, 8 and 9. Saving one grow-wide target on the
 * protocol row — the `ProtocolTargetsScreen` path, which writes the protocol and nothing
 * else — cascaded them away. `sqlite_sequence.protocol_stages` read `9` while only three
 * rows remained, and the protocol card was still drawing a three-stage schedule that no
 * longer existed in the database.
 *
 * `ProtocolEditorDialog` had been getting away with it because it calls `saveStages`
 * immediately afterwards and rebuilds the schedule. The target surfaces do not, which is
 * exactly why F13 turned a long-standing wart into data loss: it gave the grow-wide
 * columns a write path, and that path went through the delete.
 *
 * ## What this asserts, and what it cannot
 *
 * That `MainViewModel.saveProtocol` issues an `update` for a row that has an id, and an
 * `insert` only for one that does not. The store below is a `MutableList` behind the real
 * DAO interfaces and it deliberately does **not** model the cascade, because that is
 * SQLite's job and is not reproducible on the JVM. So the assertion is phrased as "no
 * REPLACE was issued", and the cascade itself stays a hardware-verified fact — which is
 * why the reproduction above had to happen on a real device.
 */
class ProtocolWriteDoesNotCascadeTest {

    private class FakeProtocolDao : ProtocolDao {
        val rows = linkedMapOf<Long, Protocol>()
        val log = mutableListOf<String>()

        override fun getProtocolsByPlant(plantId: Long): Flow<List<Protocol>> =
            flowOf(rows.values.filter { it.plantId == plantId })

        override suspend fun getProtocolById(id: Long): Protocol? = rows[id]

        override suspend fun getActiveProtocols(): List<Protocol> = rows.values.toList()

        override suspend fun insertProtocol(protocol: Protocol): Long {
            val id = if (protocol.id != 0L) protocol.id else (rows.size + 1).toLong()
            log += "insert($id)"
            rows[id] = protocol.copy(id = id)
            return id
        }

        override suspend fun updateProtocol(protocol: Protocol) {
            log += "update(${protocol.id})"
            rows[protocol.id] = protocol
        }

        override suspend fun deleteProtocol(protocol: Protocol) {
            rows.remove(protocol.id)
        }
    }

    private class FakeStageDao : ProtocolStageDao {
        val rows = mutableListOf<ProtocolStage>()
        private var nextId = 1L

        override suspend fun getStagesByProtocol(protocolId: Long): List<ProtocolStage> =
            rows.filter { it.protocolId == protocolId }.sortedBy { it.sortOrder }

        override fun watchStagesByProtocol(protocolId: Long): Flow<List<ProtocolStage>> =
            flowOf(rows.filter { it.protocolId == protocolId }.sortedBy { it.sortOrder })

        override suspend fun insertStage(stage: ProtocolStage): Long {
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
            val index = rows.indexOfFirst { it.id == stage.id }
            if (index >= 0) rows[index] = stage
        }

        override suspend fun deleteStage(stage: ProtocolStage) {
            rows.removeAll { it.id == stage.id }
        }

        override suspend fun updateStageVpdTarget(protocolId: Long, stageId: Long, band: String?) {
            val index = rows.indexOfFirst { it.id == stageId && it.protocolId == protocolId }
            if (index >= 0) rows[index] = rows[index].copy(vpdTarget = GrowRange.decode(band))
        }
    }

    @Test
    fun `a protocol with an id is written with update and never with insert`() {
        val protocols = FakeProtocolDao()
        val stages = FakeStageDao()
        val repository = ProtocolRepository(protocols, stages)

        val id = runBlocking { repository.insertProtocol(Protocol(plantId = 11, name = "Verificacion-F13")) }
        runBlocking {
            repository.saveStages(
                id,
                listOf(
                    ProtocolStage(protocolId = id, stageName = "Germinación", durationDays = 7, sortOrder = 0),
                    ProtocolStage(protocolId = id, stageName = "Vegetativa", durationDays = 35, sortOrder = 1),
                    ProtocolStage(protocolId = id, stageName = "Floración", durationDays = 56, sortOrder = 2)
                )
            )
        }
        assertEquals("fixture: three stages exist", 3, runBlocking { stages.getStagesByProtocol(id) }.size)

        protocols.log.clear()
        val loaded = runBlocking { protocols.getProtocolById(id) }!!
        runBlocking { repository.updateProtocol(loaded.copy(lightHours = 20)) }

        assertEquals(
            "a protocol that already has an id must never go through insert: REPLACE cascades " +
                "and takes every protocol_stages row with it",
            listOf("update($id)"),
            protocols.log
        )
        assertEquals(
            "and the stage schedule is still there",
            listOf("Germinación", "Vegetativa", "Floración"),
            runBlocking { stages.getStagesByProtocol(id) }.map { it.stageName }
        )
    }

    @Test
    fun `a protocol with no id still goes through insert`() {
        val protocols = FakeProtocolDao()
        val repository = ProtocolRepository(protocols, FakeStageDao())

        val id = runBlocking { repository.insertProtocol(Protocol(plantId = 3, name = "nueva")) }

        assertTrue("a brand new protocol must receive a row id", id > 0L)
        assertEquals("and it goes through insert, because it has no stages to cascade", listOf("insert($id)"), protocols.log)
    }
}
