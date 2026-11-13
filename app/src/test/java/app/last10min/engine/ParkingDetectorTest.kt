package app.last10min.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ParkingDetectorTest {
    private val zones = listOf(LOT_A)
    private val detector = ParkingDetector()
    private val events = mutableListOf<DetectedEvent>()

    private fun at(
        sec: Long,
        loc: LatLng?,
        speed: Double = 5.0,
        gear: Gear = Gear.DRIVE,
        seat: Map<SeatPos, Boolean?> = seats(SeatPos.DRIVER),
        doors: Map<SeatPos, Boolean?> = allClosed(),
    ) {
        events += detector.update(VehicleState(sec * 1000, loc, speed, gear, seat, doors), zones)
    }

    @Test
    fun `slow entry then P, driver door open and driver seat empty completes parking timed from entry`() {
        at(0, OUTSIDE_A, speed = 25.0)
        at(10, INSIDE_A)
        at(30, INSIDE_A)
        at(400, INSIDE_A, speed = 0.0, gear = Gear.PARK)
        at(410, INSIDE_A, speed = 0.0, gear = Gear.PARK, doors = allClosed() + (SeatPos.DRIVER to true))
        at(412, INSIDE_A, speed = 0.0, gear = Gear.PARK, seat = seats(), doors = allClosed() + (SeatPos.DRIVER to true))

        assertEquals(listOf(EventKind.PARK_ENTER, EventKind.PARK_COMPLETE), events.map { it.kind })
        assertEquals(402, events.last().durationSec)
        assertEquals("lot-a", events.last().zoneId)
        assertTrue(detector.state is ParkingDetector.State.Parked)
    }

    @Test
    fun `passing through the lot without dwelling emits nothing`() {
        at(0, INSIDE_A, speed = 8.0)
        at(5, INSIDE_A, speed = 8.0)
        at(9, OUTSIDE_A, speed = 8.0)
        at(10, INSIDE_A, speed = 40.0)

        assertTrue(events.isEmpty())
    }

    @Test
    fun `leaving after dwelling without ever selecting P is a parking failure`() {
        at(0, INSIDE_A)
        at(20, INSIDE_A)
        at(300, OUTSIDE_A)

        assertEquals(listOf(EventKind.PARK_ENTER, EventKind.PARK_FAIL), events.map { it.kind })
        assertEquals(300, events.last().durationSec)
    }

    @Test
    fun `driver waiting in the car in P for three minutes counts as parked at the moment of P`() {
        at(0, INSIDE_A)
        at(20, INSIDE_A)
        at(100, INSIDE_A, speed = 0.0, gear = Gear.PARK)
        at(279, INSIDE_A, speed = 0.0, gear = Gear.PARK)
        assertEquals(listOf(EventKind.PARK_ENTER), events.map { it.kind })

        at(280, INSIDE_A, speed = 0.0, gear = Gear.PARK)
        assertEquals(EventKind.PARK_COMPLETE, events.last().kind)
        assertEquals(100, events.last().durationSec)
    }

    @Test
    fun `without a seat sensor, P plus driver door open is enough`() {
        val unknownSeats = SeatPos.entries.associateWith { null as Boolean? }
        at(0, INSIDE_A, seat = unknownSeats)
        at(20, INSIDE_A, seat = unknownSeats)
        at(60, INSIDE_A, speed = 0.0, gear = Gear.PARK, seat = unknownSeats, doors = allClosed() + (SeatPos.DRIVER to true))

        assertEquals(EventKind.PARK_COMPLETE, events.last().kind)
    }

    @Test
    fun `exit is timed from P to D until leaving the lot, surviving underground GPS loss`() {
        at(0, INSIDE_A, speed = 0.0, gear = Gear.PARK, seat = seats())
        assertTrue(detector.state is ParkingDetector.State.Parked)

        at(1000, null, speed = 0.0, gear = Gear.DRIVE)
        at(1100, null, speed = 10.0)
        at(1180, OUTSIDE_A, speed = 15.0)

        assertEquals(listOf(EventKind.EXIT_COMPLETE), events.map { it.kind })
        assertEquals(180, events.single().durationSec)
    }
}
