package com.parkdeal.core.navi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DestinationFollowerTest {
    private val burger = Place("필앤필버거 강남본점", 37.4997, 127.0349)
    private val lot = Place("아크플레이스", 37.4998, 127.0350)

    @Test fun `처음 받은 목적지는 새 목적지다`() {
        val f = DestinationFollower()
        assertEquals(burger, f.onDestination(burger))
        assertEquals(burger, f.current)
    }

    @Test fun `같은 목적지가 다시 오면 무시한다`() {
        val f = DestinationFollower().apply { onDestination(burger) }
        assertNull(f.onDestination(burger.copy(lat = 37.49971)))
    }

    @Test fun `이름이 빈 목적지는 무시한다`() {
        assertNull(DestinationFollower().onDestination(Place(" ", 37.0, 127.0)))
    }

    @Test fun `앱이 안내한 주차장으로 목적지가 바뀌면 원래 목적지를 유지한다`() {
        val f = DestinationFollower().apply { onDestination(burger) }
        f.guidingTo("아크플레이스")
        assertNull(f.onDestination(lot))
        assertEquals(burger, f.current)
    }

    @Test fun `안내 중이어도 다른 곳으로 목적지를 바꾸면 새 목적지다`() {
        val f = DestinationFollower().apply { onDestination(burger); guidingTo("아크플레이스") }
        val other = Place("조이치과", 37.48, 126.94)
        assertEquals(other, f.onDestination(other))
        assertNull(f.guiding)
    }
}
