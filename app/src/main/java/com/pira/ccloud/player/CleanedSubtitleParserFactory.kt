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
                val cleanedText = text?.let { stripNonRenderableContent(it) }
                if (cleanedText != null && !contentEquals(cleanedText, text)) {
                    changed = true
                    cue.buildUpon().setText(cleanedText).build()
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

        private fun contentEquals(a: CharSequence, b: CharSequence): Boolean =
            a.toString() == b.toString()

        /**
         * Removes content Media3's SSA parser leaves behind as plain, literal text
         * because it has no renderer for it:
         *  - leftover override-tag blocks themselves, e.g. "{\fad(200,200)}",
         *    "{\pos(400,300)}", "{\k34}", including an explicit "{\p1}...{\p0}"
         *    drawing-mode pair if one is still present in the text;
         *  - bare vector-drawing path data, e.g. "m 50 0 b 22 0 0 22 0 50 ...".
         *    This is the common case in practice: Media3 recognizes "\pN" as a
         *    known tag and consumes it as part of its own override-tag handling
         *    even though it can't render the resulting shape, so by the time this
         *    text reaches us there is no "{\p1}" tag left to detect - only the raw
         *    coordinate/command text it was wrapping remains, with nothing marking
         *    it as non-text. That's identified here purely by its shape: runs of a
         *    single drawing-command letter (m/n/l/b/s/c, per the ASS spec) each
         *    followed by one or more numbers, repeated. Real dialogue essentially
         *    never looks like that, so this is safe to strip without a tag to key
         *    off - and only the matching runs are removed, so other real text in
         *    the same cue (e.g. karaoke lyrics placed next to a decorative drawn
         *    shape) is left untouched.
         */
        private fun stripNonRenderableContent(text: CharSequence): CharSequence {
            var result: CharSequence = text

            if (OVERRIDE_BLOCK_REGEX.containsMatchIn(result)) {
                result = stripOverrideBlocksAndExplicitDrawingMode(result)
            }

            if (DRAWING_RUN_REGEX.containsMatchIn(result)) {
                val builder = SpannableStringBuilder(result)
                DRAWING_RUN_REGEX.findAll(result).toList().asReversed().forEach { match ->
                    builder.delete(match.range.first, match.range.last + 1)
                }
                result = builder
            }

            return result
        }

        private fun stripOverrideBlocksAndExplicitDrawingMode(text: CharSequence): CharSequence {
            val builder = SpannableStringBuilder(text)
            val deleteRanges = mutableListOf<IntRange>()
            var drawingMode = false
            var lastEnd = 0

            for (match in OVERRIDE_BLOCK_REGEX.findAll(text)) {
                if (drawingMode && match.range.first > lastEnd) {
                    // Plain text that appeared while still in drawing mode - this
                    // is vector path data (coordinates/commands), not words.
                    deleteRanges.add(lastEnd until match.range.first)
                }
                // The override block itself is never shown as literal text.
                deleteRanges.add(match.range.first..match.range.last)

                val pTag = DRAWING_MODE_REGEX.find(match.value)
                if (pTag != null) {
                    drawingMode = (pTag.groupValues[1].toIntOrNull() ?: 0) > 0
                }
                lastEnd = match.range.last + 1
            }
            if (drawingMode && lastEnd < text.length) {
                deleteRanges.add(lastEnd until text.length)
            }

            // Delete back-to-front so earlier ranges stay valid as later ones
            // are removed.
            for (range in deleteRanges.asReversed()) {
                builder.delete(range.first, range.last + 1)
            }
            return builder
        }
    }

    companion object {
        // Matches ASS/SSA override blocks such as "{\pos(10,20)}", "{\fad(200,200)}",
        // "{\k34}" or "{\an8\fad(0,500)}" - anything starting with "{\" up to the
        // next "}".
        private val OVERRIDE_BLOCK_REGEX = Regex("\\{\\\\[^}]*\\}")

        // Finds a "\pN" drawing-mode scale tag inside an override block's contents.
        private val DRAWING_MODE_REGEX = Regex("""\\p(\d+)""")

        // Matches a run of 2 or more consecutive ASS drawing-path command groups -
        // a single letter from m/n/l/b/s/c (moveto/lineto/bezier/spline/close)
        // followed by one or more numbers - with nothing but whitespace between
        // groups, e.g. "m 0 0 l 45 80 l 0 160" or "m 50 0 b 22 0 0 22 0 50". Two or
        // more groups are required (rather than just one) to keep this from ever
        // matching a stray real word that happens to be a single letter followed
        // by a number.
        private val DRAWING_RUN_REGEX = Regex(
            "(?:\\b[mnlbsc](?:\\s+-?\\d+(?:\\.\\d+)?)+\\s*){2,}"
        )
    }
}
