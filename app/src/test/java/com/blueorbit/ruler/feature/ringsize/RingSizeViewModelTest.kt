package com.blueorbit.ruler.feature.ringsize

import com.blueorbit.ruler.core.calibration.Calibration
import com.blueorbit.ruler.core.calibration.CalibrationSource
import com.blueorbit.ruler.core.calibration.CalibrationStore
import com.blueorbit.ruler.core.calibration.InMemoryCalibrationStore
import com.blueorbit.ruler.core.entitlement.FakeEntitlementRepository
import com.blueorbit.ruler.core.geometry.RingSizeSystem
import com.blueorbit.ruler.core.log.LogRepository
import com.blueorbit.ruler.core.log.Measurement
import com.blueorbit.ruler.core.log.MeasurementDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * M21's screen decisions, held without a device: what the plate drew becomes which measurement, the
 * fine buttons land on a published size rather than a millimetre, nothing is claimed before the user
 * has drawn something, and the row that reaches the log is a diameter with the size in its name.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class RingSizeViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private class RowsDao : MeasurementDao {
        val rows = ArrayList<Measurement>()
        private val flow = MutableStateFlow<List<Measurement>>(emptyList())
        private var nextId = 0L

        override suspend fun insert(measurement: Measurement): Long {
            val row = measurement.copy(id = ++nextId)
            rows += row
            flow.value = rows
            return row.id
        }

        override suspend fun update(measurement: Measurement) = 0
        override suspend fun delete(measurement: Measurement) = 0
        override fun observeAll(): Flow<List<Measurement>> = flow
        override suspend fun count() = rows.size
        override suspend fun findById(id: Long) = rows.firstOrNull { it.id == id }
        override suspend fun deleteById(id: Long) = 0
    }

    /** A card calibration: 15 px/mm and the card's own 0.19 mm per 100 mm budget. */
    private fun store() = InMemoryCalibrationStore(
        Calibration(15.0, CalibrationSource.CARD, 0.10 / 53.98 * 100.0)
    )

    /** The unverified device estimate the screen is seeded with before the stored read lands. */
    private val guess = Calibration(14.0, CalibrationSource.DEVICE_DEFAULT, 5.0)

    private fun model(dao: RowsDao = RowsDao(), calibrationStore: CalibrationStore = store()) =
        RingSizeViewModel(
            calibrationStore,
            LogRepository(dao, FakeEntitlementRepository(unlocked = true)),
            guess
        )

    @Test
    fun nothingIsClaimedBeforeTheUserHasDrawnACircle() = runTest {
        val vm = model()
        assertEquals(0.0, vm.innerDiameterMm(), 1e-9)
        assertNull(vm.match())
        assertEquals(0L, vm.save("Ring size"))
    }

    @Test
    fun aDrawnCircleIsReadAsItsInnerDiameter() = runTest {
        val vm = model()
        vm.setDrawn(17_323L, maxMicrometres = 100_000L)
        assertEquals(17.323, vm.innerDiameterMm(), 0.001)
        assertEquals("7", vm.match(RingSizeSystem.US)?.nearest?.label)
        assertEquals("54", vm.match(RingSizeSystem.EU)?.nearest?.label)
    }

    @Test
    fun aPaperStripIsReadAsACircumferenceAndGivesTheSameRing() = runTest {
        val vm = model()
        vm.setMode(RingMeasureMode.FINGER)
        // 54.4 mm of paper round the finger is the circumference of a US 7 ring.
        vm.setDrawn(54_400L, maxMicrometres = 200_000L)
        assertEquals(17.32, vm.innerDiameterMm(), 0.05)
        assertEquals("7", vm.match(RingSizeSystem.US)?.nearest?.label)
    }

    @Test
    fun switchingMethodClearsTheDrawingRatherThanReinterpretingIt() = runTest {
        val vm = model()
        vm.setDrawn(17_323L, maxMicrometres = 100_000L)
        vm.setMode(RingMeasureMode.FINGER)
        // 17.3 mm read as a circumference would be a 5.5 mm ring, which is a different object entirely.
        assertEquals(0.0, vm.innerDiameterMm(), 1e-9)
        assertNull(vm.match())
    }

    @Test
    fun theFineButtonsMoveToTheNextPublishedSizeNotByAFixedMillimetre() = runTest {
        val vm = model()
        vm.setDrawn(17_323L, maxMicrometres = 100_000L)

        vm.stepRung(+1, maxMicrometres = 100_000L)
        // US 7½ is 17.7292 mm, so the drawing lands exactly on a published rung.
        assertEquals(17_729L, vm.state.value.drawnMicrometres)
        assertEquals("7½", vm.match(RingSizeSystem.US)?.nearest?.label)

        vm.stepRung(-1, maxMicrometres = 100_000L)
        assertEquals(17_323L, vm.state.value.drawnMicrometres)

        vm.stepRung(-1, maxMicrometres = 100_000L)
        assertEquals(16_916L, vm.state.value.drawnMicrometres)
        assertEquals("6½", vm.match(RingSizeSystem.US)?.nearest?.label)
    }

    @Test
    fun aButtonCannotAskForARingThisPlateCannotDraw() = runTest {
        val vm = model()
        vm.setDrawn(17_323L, maxMicrometres = 100_000L)
        // The plate on this screen reaches 18 mm; US 7½ needs 17.73 mm of circle but the plate was
        // measured in a mode where the drawing needs more than the screen has.
        vm.stepRung(+1, maxMicrometres = 17_400L)
        assertEquals(17_323L, vm.state.value.drawnMicrometres)
    }

    @Test
    fun aButtonAtTheEndOfTheLadderStaysOnTheLastSize() = runTest {
        val vm = model()
        // US 16 is 24.638 mm, the last rung the ladder publishes.
        vm.setDrawn(24_638L, maxMicrometres = 100_000L)
        vm.stepRung(+1, maxMicrometres = 1_000_000L)
        assertEquals(24_638L, vm.state.value.drawnMicrometres)
        assertEquals("16", vm.match(RingSizeSystem.US)?.nearest?.label)
    }

    @Test
    fun theDrawingIsClampedToWhatThePlateCanDraw() = runTest {
        val vm = model()
        vm.setDrawn(90_000L, maxMicrometres = 20_000L)
        assertEquals(20_000L, vm.state.value.drawnMicrometres)
        assertEquals(20.0, vm.innerDiameterMm(), 1e-9)
    }

    @Test
    fun aSmallerWindowLimitsTheReadingButDoesNotDiscardTheDrawing() = runTest {
        val vm = model()
        vm.setDrawn(17_323L, maxMicrometres = 100_000L)
        // Turn the phone: this window can now only draw a 15 mm circle.
        vm.setPlateLimit(15_000L)
        assertEquals(17_323L, vm.state.value.drawnMicrometres)
        // What the screen reads out is the circle it can actually draw, never the one it cannot.
        assertEquals(15_000L, vm.drawableMicrometres)
        assertEquals(15.0, vm.innerDiameterMm(), 1e-9)
        // Turn it back and the alignment is where the user left it.
        vm.setPlateLimit(100_000L)
        assertEquals(17_323L, vm.drawableMicrometres)
    }

    @Test
    fun theSizeRangeIsWideBeforeCalibrationAndNarrowAfter() = runTest {
        val uncalibrated = model(calibrationStore = InMemoryCalibrationStore(
            Calibration(15.0, CalibrationSource.DEVICE_DEFAULT, 5.0)
        ))
        uncalibrated.setDrawn(17_323L, maxMicrometres = 100_000L)
        val (wideLow, wideHigh) = uncalibrated.sizeRange(RingSizeSystem.EU)
        assertTrue(
            "an uncalibrated reading must name a range, was $wideLow–$wideHigh",
            wideHigh.toInt() - wideLow.toInt() >= 4
        )

        val calibrated = model()
        calibrated.setDrawn(17_323L, maxMicrometres = 100_000L)
        val (low, high) = calibrated.sizeRange(RingSizeSystem.EU)
        // The circle drawn at 17.32 mm sits 0.42 EU sizes above size 54, so even a card calibration
        // can only narrow the answer to the two sizes it falls between — never to a promise of one.
        assertTrue(
            "a card calibration must narrow the answer to one rung, was $low–$high",
            high.toInt() - low.toInt() <= 1
        )
        assertTrue(
            "a card calibration must be worth well under one EU size, was ${calibrated.sizesOfUncertainty(RingSizeSystem.EU)}",
            calibrated.sizesOfUncertainty(RingSizeSystem.EU) < 0.2
        )
    }

    @Test
    fun theToleranceIsTheStoredBudgetScaledToTheCircleBeingMeasured() = runTest {
        val vm = model()
        vm.setDrawn(20_000L, maxMicrometres = 100_000L)
        val per100mm = 0.10 / 53.98 * 100.0
        assertEquals(20.0 * per100mm / 100.0, vm.diameterToleranceMm(), 1e-6)
    }

    @Test
    fun theHapticStepIsTheSelectedSystemsRungInMicrometres() = runTest {
        val vm = model()
        assertEquals(406L, vm.rungStepMicrometres())
        vm.setSystem(RingSizeSystem.EU)
        assertEquals(318L, vm.rungStepMicrometres())
    }

    @Test
    fun thePaperMethodTicksOncePerMillimetreOfPaperNotPerMillimetreOfDiameter() = runTest {
        val vm = model()
        vm.setMode(RingMeasureMode.FINGER)
        vm.setSystem(RingSizeSystem.EU)
        // An EU size is 1 mm of *circumference*, and in this method the plate draws a circumference, so
        // a tick every 318 µm of paper would buzz three times per size.
        assertEquals(1_000L, vm.rungStepMicrometres())
    }

    @Test
    fun savingStoresTheDiameterWithTheSizeInTheName() = runTest {
        val dao = RowsDao()
        val vm = model(dao)
        vm.setDrawn(17_323L, maxMicrometres = 100_000L)
        val id = vm.save("Ring size US 7")
        assertTrue(id != 0L)
        val row = dao.rows.single()
        assertEquals(17_323L, row.valueMicrometres)
        assertEquals("MM", row.unit)
        assertEquals("Ring size US 7", row.label)
        assertEquals(CalibrationSource.CARD.name, row.source)
        assertEquals(0.10 / 53.98 * 100.0, row.errorBudgetMmPer100Mm, 1e-9)
    }

    @Test
    fun theSavedRowIsDatedWhenItWasSavedBecauseTheLogSortsByDate() = runTest {
        val dao = RowsDao()
        val vm = RingSizeViewModel(
            store(),
            LogRepository(dao, FakeEntitlementRepository(unlocked = true)),
            guess,
            clock = { 1_700_000_000_000L }
        )
        vm.setDrawn(17_323L, maxMicrometres = 100_000L)
        vm.save("Ring size US 7")
        // A row dated 1 January 1970 sits under every other row in a log that sorts by date, which is how
        // the first device pass at M21 read it: a row that looked broken rather than a row that was new.
        assertEquals(1_700_000_000_000L, dao.rows.single().createdAtMillis)
    }

    @Test
    fun aFingerMeasurementIsStoredAsTheRingItDescribesNotAsThePaperLength() = runTest {
        val dao = RowsDao()
        val vm = model(dao)
        vm.setMode(RingMeasureMode.FINGER)
        vm.setDrawn(54_400L, maxMicrometres = 200_000L)
        vm.save("Ring size US 7")
        val row = dao.rows.single()
        // The log holds the inner diameter, so a ring row and a ruler row are comparable lengths.
        assertTrue(
            "expected the inner diameter near 17.32 mm, was ${row.valueMicrometres} µm",
            kotlin.math.abs(17_320L - row.valueMicrometres) <= 30L
        )
    }
}
