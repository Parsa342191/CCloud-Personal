package com.pira.ccloud.player

import android.text.SpannableStringBuilder
import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.util.Consumer
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.CuesWithTiming
import androidx.media3.extractor.text.SubtitleParser

/**
 * Wraps subtitle parsing (SSA/ASS in particular) to strip leftover style-override
 * tag blocks - e.g. "{\fad(200,200)}", "{\pos(400,300)}", "{\k34}", "{\an8}" - that
 * Media3's built-in parsers don't fully understand or apply.
 *
 * Without this, those raw tag characters (including the numeric timings/positions/
 * karaoke durations inside them) print out as literal, visible text right next to
 * the actual dialogue line, instead of either being applied as an effect or at
 * least hidden - the way a full ASS renderer (e.g. libass, which MX Player and VLC
 * use) would handle them. Media3 does not ship a libass-equivalent renderer, so
 * fancy ASS effects (fades, karaoke highlighting, movement, custom positioning)
 * still won't actually animate - this is a text-level safety net only, it just
 * stops their raw tag codes from leaking into what the viewer reads.
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
            val cleanedCues = input.cues.map { cue ->
                val text = cue.text
                if (text != null && LEFTOVER_TAG_REGEX.containsMatchIn(text)) {
                    changed = true
                    val builder = SpannableStringBuilder(text)
                    // Delete matches back-to-front so earlier match ranges stay valid
                    // as later ones are removed.
                    LEFTOVER_TAG_REGEX.findAll(text).toList().asReversed().forEach { match ->
                        builder.delete(match.range.first, match.range.last + 1)
                    }
                    cue.buildUpon().setText(builder).build()
                } else {
                    cue
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
        private val LEFTOVER_TAG_REGEX = Regex("\\{\\\\[^}]*\\}")
    }
}
