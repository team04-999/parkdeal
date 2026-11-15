package com.parkdeal.core.navi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookmarksTest {
    private val home = Bookmark("우리 집", 37.5345, 127.0745, Bookmark.Kind.HOME)
    private val office = Bookmark("회사", 37.4997, 127.0350, Bookmark.Kind.OFFICE)
    private val grandma = Bookmark("할머니 집", 37.48, 126.94, Bookmark.Kind.OTHER)

    @Test fun `목적지가 집·회사 150m 안이면 그 즐겨찾기`() {
        val b = Bookmarks(listOf(home, office, grandma))
        assertEquals(home, b.routine(Place("집", 37.5350, 127.0748)))   // 약 60m
        assertEquals(office, b.routine(Place("회사", 37.4997, 127.0350)))
    }

    @Test fun `150m 밖이거나 다른 즐겨찾기(할머니 집)는 평소 목적지로 보지 않는다`() {
        val b = Bookmarks(listOf(home, grandma))
        assertNull(b.routine(Place("근처 가게", 37.5370, 127.0745)))   // 약 280m
        assertNull(b.routine(Place("할머니 집", 37.48, 126.94)))
    }
}
