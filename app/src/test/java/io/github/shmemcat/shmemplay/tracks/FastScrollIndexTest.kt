package io.github.shmemcat.shmemplay.tracks

import org.junit.Assert.assertEquals
import org.junit.Test

class FastScrollIndexTest {
    @Test
    fun labelsLatinNumbersSymbolsAndCommonScripts() {
        assertEquals("A", FastScrollIndex.bucketFor("  Åfterglow"))
        assertEquals("#", FastScrollIndex.bucketFor("1000 gecs"))
        assertEquals("•", FastScrollIndex.bucketFor("!Forward"))
        assertEquals("KR", FastScrollIndex.bucketFor("그대라는 시"))
        assertEquals("CN", FastScrollIndex.bucketFor("光年之外"))
        assertEquals("JP", FastScrollIndex.bucketFor("夜に駆ける"))
        assertEquals("TH", FastScrollIndex.bucketFor("รักติดไซเรน"))
        assertEquals("CY", FastScrollIndex.bucketFor("Группа крови"))
        assertEquals("AR", FastScrollIndex.bucketFor("تملي معاك"))
        assertEquals("GE", FastScrollIndex.bucketFor("ქართული სიმღერა"))
    }

    @Test
    fun targetsContainOnlyPresentBucketsAndPointToFirstItem() {
        assertEquals(
            listOf(
                FastScrollTarget("#", 0),
                FastScrollTarget("A", 1),
                FastScrollTarget("KR", 3),
                FastScrollTarget("TH", 5),
            ),
            FastScrollIndex.targets(
                listOf("2001", "Afterglow", "All Mirrors", "그대라는 시", "좋은 날", "รักติดไซเรน"),
            ),
        )
    }
}
