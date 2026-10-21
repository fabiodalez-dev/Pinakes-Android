package com.pinakes.app

import com.pinakes.app.data.model.DigitalAttachment
import com.pinakes.app.ui.screens.detail.playingAudioUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A playing audiobook keeps its player when another file of the record is renewed. */
class PlayingAudioUrlTest {
    private val started = DigitalAttachment("https://library.example/uploads/digital/book.mp3?pk_exp=1000&pk_uid=3&pk_sig=old", access = "granted")
    private val other = DigitalAttachment("https://library.example/uploads/digital/book.epub?pk_exp=1000&pk_uid=3&pk_sig=old", access = "granted")

    /** Renewing the eBook re-signs the audio URL too; the card still plays the URL it started with. */
    @Test fun aRenewedSignatureKeepsTheStartedUrl() {
        val resigned = started.copy(url = started.url.replace("1000", "4102444800").replace("old", "new"))
        assertEquals(started.url, playingAudioUrl(started, resigned))
    }

    /** Another file, or no started audio, shows the play button instead of a player. */
    @Test fun otherFilesAndNoSelectionHaveNoPlayer() {
        assertNull(playingAudioUrl(started, other))
        assertNull(playingAudioUrl(null, started))
    }
}
