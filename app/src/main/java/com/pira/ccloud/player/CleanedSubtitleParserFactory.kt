package com.pira.ccloud.player

import android.text.SpannableStringBuilder
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.util.Consumer
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.SubtitleParser

/**
 * Wraps subtitle parsing (SSA/ASS in particular) to strip two kinds of
 * leftover garbage that Media3's built-in parser doesn't fully understand:
 *
 * 1. Plain style-override tags - e.g. "{\fad(200,200)}", "{\pos(400,300)}",
 *    "{\k34}", "{\an8}" - which otherwise print out as literal, visible
 *    text right next to the dialogue line.
 * 2. Vector "drawing mode" spans - "{\p1}m 0 0 l 100 0 100 100 0 100{\p0}".
 *    Between a non-zero \pN and the matching \p0, the cue's "text" isn't
 *    dialogue at all, it's raw vector path data (move/line/bezier commands)
 *    meant to be rendered as a shape - e.g. for karaoke progress boxes.
 *    Media3 has no vector renderer for this (no libass-equivalent), so
 *    without this cleanup it just prints the path commands as if they were
 *    subtitle text ("m 0 0 m 100 100 tsu", "m 50 0 b 22 0 22 0 58 ..."):
 *    this is exactly the garbled strings a MediaCodec/Media3-only player
 *    shows for these lines. There's no way to actually draw the shape
 *    without a full ASS renderer, so the whole span - tags and path data
 *    together - is simply removed.
 *
 * Neither fix makes advanced ASS effects (fades, karaoke highlighting,
 * movement, drawn decorations) actually render - that still needs a full
 * ASS renderer like libass (what MX Player/VLC use), which Media3 doesn't
 * ship. This is a text-level safety net: it only stops the raw tag/path
 * codes from leaking into what the viewer reads.
 */
@OptIn(UnstableApi::class)
class CleanedSubtitleParserFactory(
    private val delegate: SubtitleParser.Factory
) : SubtitleParser.Factory {

    override fun supportsFormat(format: Format): Boolean = delegate.supportsFormat(format)

    override fun getCueReplacementBehavior(format: Format): Int =
        delegate.getCueReplacementBehavior(format)

    override fun create(format: Format): SubtitleParser =
        CleaningSubtitleParser(delegate.create(format))

    /**
     * Explicitly implements every [SubtitleParser] member itself (rather than using
     * Kotlin's `by` interface delegation) so there's no ambiguity about which
     * overload ends up calling the cleaning logic - [SubtitleParser] has a
     * convenience 3-argument `parse` that just forwards to the 5-argument one, and
     * with `by`-delegation that forwarding would resolve against the wrapped
     * parser directly and skip this class's override entirely.
     */
    private class CleaningSubtitleParser(
        private val inner: SubtitleParser
    ) : SubtitleParser {

        override fun parse(
            data: ByteArray,
            outputOptions: SubtitleParser.OutputOptions,
            output: Consumer<CuesWithTiming>
        ) {
            parse(data, 0, data.size, outputOptions, output)
        }

        override fun parse(
            data: ByteArray,
            offset: Int,
            length: Int,
            outputOptions: SubtitleParser.OutputOptions,
            output: Consumer<CuesWithTiming>
        ) {
            inner.parse(data, offset, length, outputOptions) { cuesWithTiming ->
                output.accept(clean(cuesWithTiming))
            }
        }

        override fun getCueReplacementBehavior(): Int = inner.getCueReplacementBehavior()

        override fun reset() {
            inner.reset()
        }

        private fun clean(input: CuesWithTiming): CuesWithTiming {
            var changed = false
            val cleanedCues = input.cues.mapNotNull { cue ->
                val text = cue.text
                if (text == null) {
                    cue
                } else {
                    val builder = SpannableStringBuilder(text)
                    var modified = false

                    // 1) Whole drawing-mode spans: {\pN}...{\p0} together with
                    //    the raw path text in between. A line can contain more
                    //    than one, so keep removing until none are left.
                    while (true) {
                        val match = DRAWING_MODE_SPAN_REGEX.find(builder) ?: break
                        builder.delete(match.range.first, match.range.last + 1)
                        modified = true
                    }

                    // 2) A \pN that opens drawing mode but is never explicitly
                    //    closed with \p0 before the line ends - treat the rest
                    //    of the line as drawing data too.
                    UNCLOSED_DRAWING_MODE_REGEX.find(builder)?.let { match ->
                        builder.delete(match.range.first, match.range.last + 1)
                        modified = true
                    }

                    // 3) Any remaining plain override tags that aren't part of
                    //    a drawing span ({\pos(...)}, {\fad(...)}, {\k34}, ...).
                    while (true) {
                        val match = OVERRIDE_TAG_REGEX.find(builder) ?: break
                        builder.delete(match.range.first, match.range.last + 1)
                        modified = true
                    }

                    when {
                        !modified -> cue
                        // The cue was pure decoration (a drawn box, a timing
                        // tag with no visible text, etc.) - drop it rather
                        // than leave an empty subtitle flashing on screen.
                        builder.isBlank() -> {
                            changed = true
                            null
                        }
                        else -> {
                            changed = true
                            cue.buildUpon().setText(builder).build()
                        }
                    }
                }
            }
            return if (changed) {
                CuesWithTiming(cleanedCues, input.startTimeUs, input.durationUs)
            } else {
                input
            }
        }
    }

    companion object {
        // Matches ASS/SSA override blocks such as "{\pos(10,20)}", "{\fad(200,200)}",
        // "{\k34}" or "{\an8\fad(0,500)}" - anything starting with "{\" up to the
        // next "}".
        private val OVERRIDE_TAG_REGEX = Regex("\\{\\\\[^}]*\\}")

        // A drawing-mode span: an override block turning vector drawing ON
        // (\p1, \p2, ...), the raw path text that follows ("m 0 0 l 100 0 ..."),
        // and the override block that turns it back OFF (\p0) - removed as one
        // unit since none of it is real dialogue text.
        private val DRAWING_MODE_SPAN_REGEX = Regex(
            "\\{[^}]*\\\\p[1-9][0-9]*[^}]*\\}[\\s\\S]*?\\{[^}]*\\\\p0(?!\\d)[^}]*\\}"
        )

        // Fallback for a \pN that was never closed with a matching \p0 within
        // the same cue - removes from that tag to the end of the line.
        private val UNCLOSED_DRAWING_MODE_REGEX = Regex(
            "\\{[^}]*\\\\p[1-9][0-9]*[^}]*\\}[\\s\\S]*$"
        )
    }
}
