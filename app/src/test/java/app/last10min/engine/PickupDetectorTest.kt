package app.last10min.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PickupDetectorTest {
    private val discoveredHotspot = rectZone("hotspot-1", ZoneType.PICKUP_HOTSPOT, ROADSIDE)
    private val zones = listOf(LOT_A, PICKUP_OFFICIAL, discoveredHotspot)
    private val detector = PickupDetector()
    private val events = mutableListOf<DetectedEvent>()

    private fun at(
        sec: Long,
        loc: LatLng?,
        speed: Double = 0.0,
        seat: Map<SeatPos, Boolean?> = seats(SeatPos.DRIVER),
        doors: Map<SeatPos, Boolean?> = allClosed(),
        belts: Map<SeatPos, Boolean?> = emptyMap(),
    ) {
        events += detector.update(VehicleState(sec * 1000, loc, speed, Gear.DRIVE, seat, doors, belts), zones)
    }

    private val rearOpen = allClosed() + (SeatPos.REAR_LEFT to true)

    @Test
    fun `child boarding in an official pickup zone is a pickup timed from zone entry`() {
        at(0, INSIDE_PICKUP, speed = 12.0)
        at(30, INSIDE_PICKUP)
        at(480, INSIDE_PICKUP, doors = rearOpen)
        at(485, INSIDE_PICKUP, seat = seats(SeatPos.DRIVER, SeatPos.REAR_LEFT), doors = rearOpen)
        at(490, INSIDE_PICKUP, seat = seats(SeatPos.DRIVER, SeatPos.REAR_LEFT))

        val e = events.single()
        assertEquals(EventKind.PICKUP_COMPLETE, e.kind)
        assertEquals("pickup-official", e.zoneId)
        assertEquals(490, e.durationSec)
        assertNull(e.cell)
    }

    @Test
    fun `drop-off outside official zones, even inside a discovered hotspot, stays a hotspot candidate on a snapped cell`() {
        val withChild = seats(SeatPos.DRIVER, SeatPos.REAR_LEFT)
        at(0, ROADSIDE, speed = 10.0, seat = withChild)
        at(10, ROADSIDE, seat = withChild)
        at(40, ROADSIDE, seat = withChild, doors = rearOpen)
        at(45, ROADSIDE, doors = rearOpen)
        at(50, ROADSIDE)

        val e = events.single()
        assertEquals(EventKind.DROPOFF_COMPLETE, e.kind)
        assertEquals(HOTSPOT_CANDIDATE, e.zoneType)
        assertEquals("cell:37.49950,127.06250", e.zoneId)
        assertEquals(40, e.durationSec)
    }

    @Test
    fun `driver getting out is parking, not a pickup`() {
        at(0, ROADSIDE)
        at(10, ROADSIDE, seat = seats(), doors = rearOpen)
        at(20, ROADSIDE, seat = seats(SeatPos.REAR_LEFT))

        assertTrue(events.isEmpty())
    }

    @Test
    fun `without rear seat sensing a buckled belt after the door cycle means pickup, otherwise unknown`() {
        val noRearSensor = seats(SeatPos.DRIVER) + (SeatPos.REAR_LEFT to null)
        at(0, INSIDE_PICKUP, seat = noRearSensor)
        at(10, INSIDE_PICKUP, seat = noRearSensor, doors = rearOpen)
        at(20, INSIDE_PICKUP, seat = noRearSensor, belts = mapOf(SeatPos.REAR_LEFT to true))
        at(30, INSIDE_PICKUP, seat = noRearSensor, doors = rearOpen)
        at(40, INSIDE_PICKUP, seat = noRearSensor)

        assertEquals(listOf(EventKind.PICKUP_COMPLETE, EventKind.BOARDING_UNKNOWN), events.map { it.kind })
    }

    @Test
    fun `passengers leaving a car inside a parking lot are not pickup events`() {
        val withPassenger = seats(SeatPos.DRIVER, SeatPos.PASSENGER)
        at(0, INSIDE_A, seat = withPassenger)
        at(10, INSIDE_A, seat = withPassenger, doors = allClosed() + (SeatPos.PASSENGER to true))
        at(20, INSIDE_A)

        assertTrue(events.isEmpty())
    }

    @Test
    fun `moving off cancels a half-finished door cycle`() {
        at(0, ROADSIDE)
        at(10, ROADSIDE, doors = rearOpen)
        at(20, ROADSIDE, speed = 20.0, seat = seats(SeatPos.DRIVER, SeatPos.REAR_LEFT))
        at(30, ROADSIDE, speed = 20.0, seat = seats(SeatPos.DRIVER, SeatPos.REAR_LEFT))

        assertTrue(events.isEmpty())
    }
}
