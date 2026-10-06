package com.xnvalabs.xnai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XnaiMindTest {
    private val policy = MindPolicy()

    @Test
    fun vmComputesSquarePlusOne() {
        val vm = MindVM(policy, emptyMap())
        val prog = listOf(Instr(MindOp.DUP), Instr(MindOp.MUL), Instr(MindOp.PUSH, 1), Instr(MindOp.ADD))
        assertEquals(10L, vm.run(prog, longArrayOf(3)))
        assertEquals(null, vm.run(listOf(Instr(MindOp.ADD)), longArrayOf(3)))
    }

    @Test
    fun vmStopsRunawayAndUnknownSkill() {
        val vm = MindVM(MindPolicy(maxStepsPerRun = 8), emptyMap())
        assertEquals(null, vm.run(List(20) { Instr(MindOp.DUP) }, longArrayOf(1)))
        assertEquals(null, vm.run(listOf(Instr(MindOp.CALL, 99)), longArrayOf(1)))
    }

    @Test
    fun programCodecRoundTrips() {
        val prog = listOf(Instr(MindOp.PUSH, -2), Instr(MindOp.CALL, 123456), Instr(MindOp.MUL))
        assertEquals(prog, ProgCodec.fromText(ProgCodec.toText(prog)))
    }

    @Test
    fun mindLearnsAndKeepsSkillsAcrossRestarts() {
        val store = InMemoryMindStore()
        val mind = XnaiMind(store, policy)
        val report = mind.runRound(seed = 11L, budgetMs = 8_000L, maxTasks = 10)
        assertTrue("some tasks should be attempted", report.attempted > 0)
        assertTrue("easy self-made tasks should be solvable", report.solved >= 1)
        assertEquals(mind.skillCount(), XnaiMind(store, policy).skillCount())
    }
}
