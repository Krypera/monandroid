package com.monandroido.data

import com.google.common.truth.Truth.assertThat
import com.monandroido.data.model.AdvancedMinerSettings
import com.monandroido.data.model.ProfileDraft
import org.junit.Test

class ProfileDraftSanityTest {
    @Test
    fun defaults_areBeginnerFriendly() {
        val draft = ProfileDraft()

        assertThat(draft.enabled).isTrue()
        assertThat(draft.advancedSettings).isEqualTo(AdvancedMinerSettings())
    }
}
