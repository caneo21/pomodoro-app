package com.pomodoro.timer.update

import com.pomodoro.timer.core.LatestRelease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubReleaseFetcherTest {

    @Test
    fun `parseLatestRelease reads tag_name and html_url`() {
        val json = """{"tag_name":"v1.2.0","html_url":"https://github.com/caneo21/pomodoro-app/releases/tag/v1.2.0","name":"x"}"""

        assertEquals(
            LatestRelease("v1.2.0", "https://github.com/caneo21/pomodoro-app/releases/tag/v1.2.0"),
            parseLatestRelease(json)
        )
    }

    @Test
    fun `parseLatestRelease returns null for missing fields, non-JSON, or rate-limit body`() {
        listOf(
            """{"message":"API rate limit exceeded"}""",
            "<html>login</html>",
            "",
            """{"tag_name":"v1.2.0"}""",
        ).forEach { assertNull("expected null for '$it'", parseLatestRelease(it)) }
    }

    @Test
    fun `parseLatestRelease rejects non-https url`() {
        assertNull(parseLatestRelease("""{"tag_name":"v1.2.0","html_url":"http://evil/x"}"""))
    }
}
