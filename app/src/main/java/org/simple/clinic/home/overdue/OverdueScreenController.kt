package org.simple.clinic.home.overdue

import io.reactivex.Observable
import io.reactivex.ObservableTransformer
import io.reactivex.rxkotlin.ofType
import io.reactivex.rxkotlin.withLatestFrom
import org.simple.clinic.ReportAnalyticsEvents
import org.simple.clinic.overdue.AppointmentRepository
import org.simple.clinic.patient.Age
import org.simple.clinic.util.RuntimePermissionResult
import org.simple.clinic.widgets.UiEvent
import org.threeten.bp.Instant
import org.threeten.bp.LocalDate
import org.threeten.bp.LocalDateTime
import org.threeten.bp.Period
import org.threeten.bp.ZoneOffset.UTC
import org.threeten.bp.temporal.ChronoUnit.DAYS
import javax.inject.Inject

typealias Ui = OverdueScreen
typealias UiChange = (Ui) -> Unit

class OverdueScreenController @Inject constructor(
    private val appointmentRepo: AppointmentRepository
) : ObservableTransformer<UiEvent, UiChange> {

  override fun apply(upstream: Observable<UiEvent>): Observable<UiChange> {
    val replayedEvents = upstream.compose(ReportAnalyticsEvents()).replay().refCount()

    return Observable.merge(
        screenSetup(replayedEvents),
        phoneCallPermissionRequests(replayedEvents),
        patientCalls(replayedEvents))
  }

  private fun screenSetup(events: Observable<UiEvent>): Observable<UiChange> {
    val dbStream = events
        .ofType<OverdueScreenCreated>()
        .flatMap { appointmentRepo.overdueAppointments() }

    val updateListStream = dbStream
        .map { appointments ->
          appointments.map {
            OverdueListItem(
                appointmentUuid = it.appointment.uuid,
                name = it.fullName,
                gender = it.gender,
                age = getAge(it.dateOfBirth, it.age),
                phoneNumber = it.phoneNumber?.number,
                bpSystolic = it.bloodPressure.systolic,
                bpDiastolic = it.bloodPressure.diastolic,
                bpDaysAgo = calculateDaysAgoFromInstant(it.bloodPressure.updatedAt),
                overdueDays = calculateOverdueDays(it.appointment.date))
          }
        }
        .map { { ui: Ui -> ui.updateList(it) } }

    val emptyStateStream = dbStream
        .map { it.isEmpty() }
        .map { { ui: Ui -> ui.handleEmptyList(it) } }

    return updateListStream.mergeWith(emptyStateStream)
  }

  private fun getAge(dateOfBirth: LocalDate?, age: Age?): Int {
    return if (age == null) {
      Period.between(dateOfBirth!!, LocalDate.now(UTC)).years

    } else {
      val ageUpdatedAt = LocalDateTime.ofInstant(age.updatedAt, UTC)
      val updatedAtLocalDate = LocalDate.of(ageUpdatedAt.year, ageUpdatedAt.month, ageUpdatedAt.dayOfMonth)
      val yearsSinceThen = Period.between(updatedAtLocalDate, LocalDate.now(UTC)).years
      val ageWhenRecorded = age.value

      ageWhenRecorded + yearsSinceThen
    }
  }

  private fun calculateDaysAgoFromInstant(instant: Instant): Int {
    val localDateTime = LocalDateTime.ofInstant(instant, UTC)
    return calculateOverdueDays(LocalDate.of(localDateTime.year, localDateTime.month, localDateTime.dayOfMonth))
  }

  private fun calculateOverdueDays(date: LocalDate): Int {
    return DAYS.between(date, LocalDate.now(UTC)).toInt()
  }

  private fun phoneCallPermissionRequests(events: Observable<UiEvent>): Observable<UiChange> {
    return events.ofType<CallPatientClicked>()
        .map { { ui: Ui -> ui.requestCallPermission() } }
  }

  private fun patientCalls(events: Observable<UiEvent>): Observable<UiChange> {
    val callPhonePermissionChanges = events
        .ofType<CallPhonePermissionChanged>()
        .map(CallPhonePermissionChanged::result)

    val callClicks = events
        .ofType<CallPatientClicked>()
        .map { it.phoneNumber }

    val withoutDialerCalls = callPhonePermissionChanges
        .filter { it == RuntimePermissionResult.GRANTED }
        .withLatestFrom(callClicks)
        .map { (_, phoneNumber) -> { ui: Ui -> ui.callPatientWithoutUsingDialer(phoneNumber) } }

    val withDialerCalls = callPhonePermissionChanges
        .filter { it != RuntimePermissionResult.GRANTED }
        .withLatestFrom(callClicks)
        .map { (_, phoneNumber) -> { ui: Ui -> ui.callPatientUsingDialer(phoneNumber) } }

    return withDialerCalls.mergeWith(withoutDialerCalls)
  }
}
