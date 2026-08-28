package botix.dev.myweeklyroutinegymapp.presentation

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import botix.dev.myweeklyroutinegymapp.data.repository.BodyWeightRepositoryImpl
import botix.dev.myweeklyroutinegymapp.db.GymDatabase
import botix.dev.myweeklyroutinegymapp.db.createDatabase
import botix.dev.myweeklyroutinegymapp.domain.Clock
import botix.dev.myweeklyroutinegymapp.domain.repository.BodyWeightRepository
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.GetBodyWeightForDayUseCase
import botix.dev.myweeklyroutinegymapp.domain.usecase.bodyweight.LogBodyWeightUseCase
import botix.dev.myweeklyroutinegymapp.presentation.bodyweight.BodyWeightEntryError
import botix.dev.myweeklyroutinegymapp.presentation.bodyweight.BodyWeightEntryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The create-or-edit screen for one day's body weight.
 *
 * The screen is keyed by *day*, not by row id, because `date_epoch_day` is UNIQUE and
 * the upsert changes a reading's id every time it is corrected. These pin that: what
 * the form opens with, what it writes, and what it refuses to write.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class BodyWeightEntryTest {

    private val dispatcher = UnconfinedTestDispatcher()

    /** Friday 21 Aug 2026. */
    private val today = 20_686L

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private class FixedClock(private val day: Long) : Clock {
        override fun nowMillis(): Long = 0L
        override fun today(): Long = day
    }

    private fun repository(): BodyWeightRepository {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        GymDatabase.Schema.create(driver)
        return BodyWeightRepositoryImpl(createDatabase(driver), dispatcher)
    }

    /** Built and opened, which is the pair of things the screen does on arrival. */
    private fun viewModel(repository: BodyWeightRepository, day: Long) = BodyWeightEntryViewModel(
        epochDay = day,
        getForDay = GetBodyWeightForDayUseCase(repository),
        logBodyWeight = LogBodyWeightUseCase(repository, FixedClock(today)),
        clock = FixedClock(today),
    ).also { it.onOpened() }

    /**
     * Collects the save signal the way the screen does — while the screen is open.
     *
     * The list is what the screen would act on: one element means it would have
     * closed itself once, and an empty one means it would still be standing there.
     */
    private fun TestScope.savedSignals(viewModel: BodyWeightEntryViewModel): List<Unit> {
        val signals = mutableListOf<Unit>()
        backgroundScope.launch(dispatcher) { viewModel.saved.collect { signals += it } }
        return signals
    }

    @Test
    fun aDayWithNothingOnItOpensEmpty() = runTest(dispatcher) {
        val state = viewModel(repository(), today).state.value

        assertFalse(state.isLoading)
        assertEquals("", state.weightInput)
        assertEquals("", state.note)
        assertNull(state.existingKg)
        // Nothing typed yet, so there is nothing to save and nothing to complain about.
        assertFalse(state.canSave)
        assertNull(state.error)
    }

    @Test
    fun aDayThatAlreadyHasAReadingOpensOnIt() = runTest(dispatcher) {
        val repository = repository()
        repository.upsert(today, 89.0, "after breakfast")

        val state = viewModel(repository, today).state.value

        // "89", not "89.0": the user typed a whole number, and handing back a decimal
        // they never entered means deleting it before they can type.
        assertEquals("89", state.weightInput)
        assertEquals("after breakfast", state.note)
        assertEquals(89.0, state.existingKg)
        assertTrue(state.canSave)
    }

    @Test
    fun aHalfKiloKeepsItsHalf() = runTest(dispatcher) {
        val repository = repository()
        repository.upsert(today, 89.5, null)

        assertEquals("89.5", viewModel(repository, today).state.value.weightInput)
    }

    @Test
    fun savingWritesTheDayTheScreenWasOpenedOn() = runTest(dispatcher) {
        val repository = repository()
        // Deliberately not today: the reading belongs to the day that was picked on
        // the strip, and defaulting to today would silently misdate every correction.
        val viewModel = viewModel(repository, today - 3)
        val saved = savedSignals(viewModel)
        viewModel.setWeight("90.5")
        viewModel.setNote("evening")
        viewModel.save()

        assertEquals(1, saved.size)
        assertEquals(90.5, repository.getByDay(today - 3)?.weightKg)
        assertEquals("evening", repository.getByDay(today - 3)?.note)
        assertNull(repository.getByDay(today))
    }

    @Test
    fun savingTwiceOnOneDayCorrectsItRatherThanAddingASecondReading() = runTest(dispatcher) {
        val repository = repository()
        viewModel(repository, today).apply {
            setWeight("90")
            save()
        }

        // Reopened, exactly as tapping the row would: the second screen sees the first
        // one's number and replaces it.
        val second = viewModel(repository, today)
        assertEquals("90", second.state.value.weightInput)
        second.setWeight("91")
        second.save()

        assertEquals(91.0, repository.getByDay(today)?.weightKg)
        // One row, not two: the day is unique, so the correction replaced it.
        assertEquals(1, repository.observeAll().first().size)
    }

    @Test
    fun reopeningADayThatWasSavedDoesNotCloseTheScreenOnArrival() = runTest(dispatcher) {
        val repository = repository()
        // The same instance the store would hand back: this ViewModel is keyed by day
        // and lives in the activity's store, so the *next* visit to this day finds it
        // exactly as the last one left it.
        val viewModel = viewModel(repository, today)
        viewModel.setWeight("90")
        viewModel.save()

        // A second visit. Nothing must reach the screen from the first one — a
        // retained "this was saved" flag closed the editor the instant it opened, so
        // tapping a reading that had ever been saved appeared to do nothing at all.
        val saved = savedSignals(viewModel)
        viewModel.onOpened()

        assertTrue(saved.isEmpty())
        // And it opens on what is stored, not on what was typed last time.
        assertEquals("90", viewModel.state.value.weightInput)
        assertEquals(90.0, viewModel.state.value.existingKg)
    }

    @Test
    fun aSecondVisitStartsFromTheDatabaseRatherThanAnAbandonedDraft() = runTest(dispatcher) {
        val repository = repository()
        repository.upsert(today, 88.0, null)

        val viewModel = viewModel(repository, today)
        // Typed, then left without saving.
        viewModel.setWeight("95")
        viewModel.onOpened()

        assertEquals("88", viewModel.state.value.weightInput)
    }

    @Test
    fun aCommaIsADecimalPoint() = runTest(dispatcher) {
        val repository = repository()
        val viewModel = viewModel(repository, today)
        // Three of the four locales this app ships write 90,5.
        viewModel.setWeight("90,5")
        viewModel.save()

        assertEquals(90.5, repository.getByDay(today)?.weightKg)
    }

    @Test
    fun textThatIsNotANumberIsRefusedAndNothingIsWritten() = runTest(dispatcher) {
        val repository = repository()
        val viewModel = viewModel(repository, today)
        viewModel.setWeight("ninety")
        viewModel.save()

        assertEquals(BodyWeightEntryError.InvalidWeight, viewModel.state.value.error)
        assertNull(repository.getByDay(today))
    }

    @Test
    fun zeroIsRefusedByTheDomainRatherThanStored() = runTest(dispatcher) {
        val repository = repository()
        val viewModel = viewModel(repository, today)
        viewModel.setWeight("0")
        viewModel.save()

        assertEquals(BodyWeightEntryError.InvalidWeight, viewModel.state.value.error)
        assertNull(repository.getByDay(today))
    }

    @Test
    fun typingAgainClearsTheErrorTheOldTextEarned() = runTest(dispatcher) {
        val repository = repository()
        val viewModel = viewModel(repository, today)
        viewModel.setWeight("ninety")
        viewModel.save()
        viewModel.setWeight("90")

        assertNull(viewModel.state.value.error)
    }

    @Test
    fun aFutureDayCannotBeSavedFromTheFormOrPastIt() = runTest(dispatcher) {
        val repository = repository()
        val viewModel = viewModel(repository, today + 1)
        viewModel.setWeight("90")

        // The button is inert...
        assertTrue(viewModel.state.value.isFuture)
        assertFalse(viewModel.state.value.canSave)

        // ...and the use case refuses it anyway, if anything ever gets past that.
        viewModel.save()
        assertEquals(BodyWeightEntryError.FutureDay, viewModel.state.value.error)
        assertNull(repository.getByDay(today + 1))
    }

    @Test
    fun anEmptyNoteIsStoredAsNothingAtAll() = runTest(dispatcher) {
        val repository = repository()
        val viewModel = viewModel(repository, today)
        viewModel.setWeight("90")
        viewModel.setNote("   ")
        viewModel.save()

        // Not an empty string: a row that carries one would render a blank third line
        // under every reading it was saved with.
        assertNull(repository.getByDay(today)?.note)
    }
}
