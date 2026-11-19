package com.emirzinciri.robotkomutan.logic

import com.emirzinciri.robotkomutan.logic.command.CommandType
import com.emirzinciri.robotkomutan.logic.command.Instr
import com.emirzinciri.robotkomutan.logic.command.Program
import com.emirzinciri.robotkomutan.logic.command.ProgramException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CommandsTest {
    @Test fun encodeDecodeRoundTrip() {
        val ins = Instr(CommandType.REPEAT, 4)
        assertEquals("REPEAT:4", ins.encode())
        assertEquals(ins, Instr.decode("REPEAT:4"))
        assertEquals(Instr(CommandType.FORWARD), Instr.decode("FORWARD"))
        assertEquals(2, Instr.decode("REPEAT").arg)
    }

    @Test fun invalidArgRejected() {
        assertFailsWith<IllegalArgumentException> { Instr(CommandType.REPEAT, 1) }
        assertFailsWith<IllegalArgumentException> { Instr(CommandType.REPEAT, 10) }
    }

    @Test fun blocksAreMatched() {
        val p = Program.parse("REPEAT:2 FORWARD IF_CLEAR FORWARD END END".split(' ')).getOrThrow()
        assertEquals(5, p.blockEnd[0])
        assertEquals(4, p.blockEnd[2])
        assertEquals(0, p.blockStart[5])
        assertEquals(4, p.cardCount) // Blok sonu sayılmaz
    }

    @Test fun unbalancedBlocksReportCardIndex() {
        val open = Program.parse(listOf("FORWARD", "REPEAT:2", "FORWARD")).exceptionOrNull() as ProgramException
        assertEquals(1, open.error.index)
        val close = Program.parse(listOf("FORWARD", "END")).exceptionOrNull() as ProgramException
        assertEquals(1, close.error.index)
    }

    @Test fun unknownTokenFails() {
        assertTrue(Program.parse(listOf("FLY_TO_MOON")).isFailure)
    }
}
