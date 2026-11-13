package app.last10min.plan

import app.last10min.data.Prediction
import app.last10min.data.PredictionKind
import app.last10min.engine.LatLng
import app.last10min.engine.ParkingFee
import app.last10min.engine.ZoneType
import app.last10min.engine.rectZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerTest {
    private val lotA = rectZone("lot-a", ZoneType.PARKING, LatLng(37.49790, 127.02760))
    private val lotB = rectZone("lot-b", ZoneType.PARKING, LatLng(37.49980, 127.02980))
    private val official = rectZone("official", ZoneType.PICKUP_OFFICIAL, LatLng(37.49870, 127.05900))
    private val hotspot = rectZone("hotspot-1", ZoneType.PICKUP_HOTSPOT, LatLng(37.49960, 127.06260))
    private val offsets = (0..120 step 10).toList()

    private fun predictions(vararg p: Prediction): Predict = { id, kind, offset ->
        p.firstOrNull { it.zoneId == id && it.kind == kind && it.offsetMin == offset }
    }

    @Test
    fun `a farther lot wins when its parking search plus the walk back is shorter`() {
        val predict = predictions(
            Prediction("lot-a", PredictionKind.PARK, 0, 9.0, 40, "history", false),
            Prediction("lot-b", PredictionKind.PARK, 0, 2.0, 40, "history", false),
        )
        val options = Planner.parkingOptions(listOf(lotA, lotB), lotA.center, null, 120, predict)

        assertEquals(listOf("lot-b", "lot-a"), options.map { it.zone.id })
        assertEquals(9.0, options.last().totalMin, 0.01)
        assertEquals(2.0 + Planner.walkMin(lotB.center, lotA.center), options.first().totalMin, 0.01)
    }

    @Test
    fun `each option carries the fee for the chosen parking duration`() {
        val priced = lotA.copy(fee = ParkingFee(30, 3_600, 10, 1_200, null))
        val options = Planner.parkingOptions(listOf(priced, lotB), lotA.center, null, 120, predictions())

        assertEquals(14_400, options.first { it.zone.id == "lot-a" }.fee)
        assertEquals(null, options.first { it.zone.id == "lot-b" }.fee)
    }

    @Test
    fun `parking fee charges every started unit and stops at the daily cap`() {
        val fee = ParkingFee(baseMin = 30, baseFee = 3_000, unitMin = 10, unitFee = 1_000, dailyMax = 10_000)

        assertEquals(0, fee.cost(0))
        assertEquals(3_000, fee.cost(30))
        assertEquals(4_000, fee.cost(31))
        assertEquals(10_000, fee.cost(240))
    }

    @Test
    fun `discovered hotspots are never offered as a place to stop`() {
        val stops = Planner.legalStops(listOf(official, hotspot, lotA), hotspot.center, radiusM = 2_000.0)

        assertEquals(listOf("official"), stops.map { it.first.id })
        assertEquals(listOf("hotspot-1"), Planner.hotspotsNear(listOf(official, hotspot), official.center).map { it.first.id })
    }

    @Test
    fun `pickup departure leaves room for the drive and the queue predicted at the arrival slot`() {
        val now = 1_794_480_000_000L
        val arrive = now + 60 * 60_000L
        val predict = predictions(Prediction("official", PredictionKind.PICKUP, 60, 6.0, 30, "history", false))

        val plan = Planner.pickupPlan(official, arrive, now, lotA.center, offsets, predict)

        assertEquals(6.0, plan.queueMin, 0.0)
        val expectedLeadMs = ((Planner.travelMin(lotA.center, official.center) + 6.0) * 60_000).toLong()
        assertEquals(arrive - expectedLeadMs, plan.departAtMs)
    }

    @Test
    fun `arrival choices start past the lead time on 10-minute marks`() {
        val now = 1_794_480_000_000L + 3 * 60_000L

        val choices = Planner.arrivalChoices(now)

        assertTrue(choices.first() >= now + 15 * 60_000L)
        assertEquals(0L, choices.first() % 600_000L)
        assertEquals(600_000L, choices[1] - choices[0])
    }
}
