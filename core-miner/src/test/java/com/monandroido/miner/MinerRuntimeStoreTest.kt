package com.monandroido.miner

import com.google.common.truth.Truth.assertThat
import com.monandroido.miner.controller.MinerRuntimeStore
import org.junit.After
import org.junit.Before
import org.junit.Test

class MinerRuntimeStoreTest {
    @Before
    fun setUp() {
        MinerRuntimeStore.clearLogs()
    }

    @After
    fun tearDown() {
        MinerRuntimeStore.clearLogs()
    }

    @Test
    fun appendLog_keepsOnlyLatestEightyLines() {
        repeat(85) { index ->
            MinerRuntimeStore.appendLog("line $index")
        }

        val logs = MinerRuntimeStore.recentLogs.value

        assertThat(logs).hasSize(80)
        assertThat(logs.first()).isEqualTo("line 5")
        assertThat(logs.last()).isEqualTo("line 84")
    }

    @Test
    fun clearLogs_removesBufferedOutput() {
        MinerRuntimeStore.appendLog("first line")

        MinerRuntimeStore.clearLogs()

        assertThat(MinerRuntimeStore.recentLogs.value).isEmpty()
    }
}
