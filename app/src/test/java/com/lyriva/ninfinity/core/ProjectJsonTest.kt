package com.lyriva.ninfinity.core

import com.lyriva.ninfinity.data.Aspect
import com.lyriva.ninfinity.data.Lang
import com.lyriva.ninfinity.data.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProjectJsonTest {
    @Test fun roundTrip() {
        val p = Project(
            audioUri = "content://x/1", title = "T", artist = "A", language = Lang.JP,
            aspect = Aspect.H, marks = listOf(0.0, 1.5), syncMode = SyncMode.GROW, bgDim = 70
        )
        val q = Project.fromJson(org.json.JSONObject(p.toJson().toString()))
        assertEquals(p, q)
    }

    @Test fun emptyJsonGivesDefaults() {
        val q = Project.fromJson(org.json.JSONObject("{}"))
        assertEquals(Project(), q)
        assertNull(q.audioUri)
    }
}
