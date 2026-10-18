package com.masjidclock.wfsender.protocol

object Command {
    const val SCREEN = 0x13
    const val START = 0x02
    const val DATA = 0x03
    const val END = 0x04
}

/** Makes the ordered frames of 1 program transfer (spec section 3.3). */
object ProgramTransfer {
    const val CHUNK_SIZE = 1000

    fun frames(export: HdsExport, transferId: Int): List<ByteArray> {
        require(transferId in 0..0xFFFF) { "transfer ID out of range: $transferId" }
        val program = export.program.copyOf()
        val chunkCount = (program.size + CHUNK_SIZE - 1) / CHUNK_SIZE
        require(chunkCount <= 0xFFFF) { "program too large: ${program.size} bytes" }

        // HD2020 writes the chunk count into the HC header. The file on disk has 0 there.
        program[9] = (chunkCount shr 8).toByte()
        program[10] = chunkCount.toByte()

        val tid = byteArrayOf(transferId.toByte(), (transferId shr 8).toByte())
        val frames = ArrayList<ByteArray>(chunkCount + 3)
        frames += HtFrame.build(Command.SCREEN, 1, export.screen)
        frames += HtFrame.build(Command.START, 2, byteArrayOf(0x05) + tid + u16be(chunkCount))
        for (i in 0 until chunkCount) {
            val chunk = program.copyOfRange(i * CHUNK_SIZE, minOf(program.size, (i + 1) * CHUNK_SIZE))
            frames += HtFrame.build(Command.DATA, 3 + i, byteArrayOf(0x05) + tid + u16be(i) + chunk)
        }
        frames += HtFrame.build(Command.END, 3 + chunkCount, byteArrayOf(0x03) + tid)
        return frames
    }

    private fun u16be(value: Int) = byteArrayOf((value shr 8).toByte(), value.toByte())
}
