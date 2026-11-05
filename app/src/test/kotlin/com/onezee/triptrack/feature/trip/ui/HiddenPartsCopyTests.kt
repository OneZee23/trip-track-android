package com.onezee.triptrack.feature.trip.ui

import com.onezee.triptrack.designsystem.strings.AppLanguage
import com.onezee.triptrack.designsystem.strings.AppStrings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HiddenPartsCopyTests {
    private val context = RuntimeEnvironment.getApplication()

    @Test
    fun localCopyUsesCountedVerbsWithAndWithoutPhotos() {
        val expected = mapOf(
            AppLanguage.ru to listOf(
                listOf("1 скрытый кусок не попадёт ни в ленту, ни в облачную копию.", "1 скрытый кусок и 1 фото оттуда не попадут ни в ленту, ни в облачную копию."),
                listOf("2 скрытых куска не попадут ни в ленту, ни в облачную копию.", "2 скрытых куска и 1 фото оттуда не попадут ни в ленту, ни в облачную копию."),
                listOf("5 скрытых кусков не попадут ни в ленту, ни в облачную копию.", "5 скрытых кусков и 1 фото оттуда не попадут ни в ленту, ни в облачную копию."),
                listOf("11 скрытых кусков не попадут ни в ленту, ни в облачную копию.", "11 скрытых кусков и 1 фото оттуда не попадут ни в ленту, ни в облачную копию."),
                listOf("21 скрытый кусок не попадёт ни в ленту, ни в облачную копию.", "21 скрытый кусок и 1 фото оттуда не попадут ни в ленту, ни в облачную копию."),
            ),
            AppLanguage.uk to listOf(
                listOf("1 прихована ділянка не потрапить ні у стрічку, ні в хмарну копію.", "1 прихована ділянка і 1 фото звідти не потраплять ні у стрічку, ні в хмарну копію."),
                listOf("2 приховані ділянки не потраплять ні у стрічку, ні в хмарну копію.", "2 приховані ділянки і 1 фото звідти не потраплять ні у стрічку, ні в хмарну копію."),
                listOf("5 прихованих ділянок не потраплять ні у стрічку, ні в хмарну копію.", "5 прихованих ділянок і 1 фото звідти не потраплять ні у стрічку, ні в хмарну копію."),
                listOf("11 прихованих ділянок не потраплять ні у стрічку, ні в хмарну копію.", "11 прихованих ділянок і 1 фото звідти не потраплять ні у стрічку, ні в хмарну копію."),
                listOf("21 прихована ділянка не потрапить ні у стрічку, ні в хмарну копію.", "21 прихована ділянка і 1 фото звідти не потраплять ні у стрічку, ні в хмарну копію."),
            ),
            AppLanguage.pl to listOf(
                listOf("1 ukryty odcinek nie trafi do kanału ani kopii w chmurze.", "1 ukryty odcinek i 1 zdjęcie stamtąd nie trafią do kanału ani kopii w chmurze."),
                listOf("2 ukryte odcinki nie trafią do kanału ani kopii w chmurze.", "2 ukryte odcinki i 1 zdjęcie stamtąd nie trafią do kanału ani kopii w chmurze."),
                listOf("5 ukrytych odcinków nie trafi do kanału ani kopii w chmurze.", "5 ukrytych odcinków i 1 zdjęcie stamtąd nie trafią do kanału ani kopii w chmurze."),
                listOf("11 ukrytych odcinków nie trafi do kanału ani kopii w chmurze.", "11 ukrytych odcinków i 1 zdjęcie stamtąd nie trafią do kanału ani kopii w chmurze."),
                listOf("21 ukrytych odcinków nie trafi do kanału ani kopii w chmurze.", "21 ukrytych odcinków i 1 zdjęcie stamtąd nie trafią do kanału ani kopii w chmurze."),
            ),
            AppLanguage.en to listOf(
                listOf("1 hidden piece won't reach the feed or cloud copy.", "1 hidden piece and 1 photo from them won't reach the feed or cloud copy."),
                listOf("2 hidden pieces won't reach the feed or cloud copy.", "2 hidden pieces and 1 photo from them won't reach the feed or cloud copy."),
                listOf("5 hidden pieces won't reach the feed or cloud copy.", "5 hidden pieces and 1 photo from them won't reach the feed or cloud copy."),
                listOf("11 hidden pieces won't reach the feed or cloud copy.", "11 hidden pieces and 1 photo from them won't reach the feed or cloud copy."),
                listOf("21 hidden pieces won't reach the feed or cloud copy.", "21 hidden pieces and 1 photo from them won't reach the feed or cloud copy."),
            ),
        )
        val counts = listOf(1, 2, 5, 11, 21)

        expected.forEach { (language, cases) ->
            val strings = AppStrings(context, language)
            counts.forEachIndexed { index, count ->
                assertEquals(cases[index][0], hiddenPartsLocalCopy(strings, count, 0))
                assertEquals(cases[index][1], hiddenPartsLocalCopy(strings, count, 1))
            }
        }
    }
}
