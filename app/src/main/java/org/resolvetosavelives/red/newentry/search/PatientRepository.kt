package org.resolvetosavelives.red.newentry.search

import io.reactivex.Completable
import io.reactivex.Observable
import io.reactivex.Single
import org.resolvetosavelives.red.AppDatabase
import java.util.UUID

class PatientRepository(private val database: AppDatabase) {

  private var ongoingPatientEntry: OngoingPatientEntry = OngoingPatientEntry()

  fun search(query: String): Observable<List<Patient>> {
    if (query.isEmpty()) {
      return database.patientDao()
          .allPatients()
          .toObservable()
    }

    return database.patientDao()
        .search(query)
        .toObservable()
  }

  // TODO: make this private.
  fun save(patient: Patient): Completable {
    return Completable.fromAction({ database.patientDao().save(patient) })
  }

  fun ongoingEntry(): Single<OngoingPatientEntry> {
    return Single.just(ongoingPatientEntry)
  }

  fun save(ongoingEntry: OngoingPatientEntry): Completable {
    return Completable.fromAction({
      this.ongoingPatientEntry = ongoingEntry
    })
  }

  fun markOngoingEntryAsComplete(patientId: UUID): Completable {
    return ongoingEntry()
        .map { entry -> entry.toPatient(patientId) }
        .flatMapCompletable { patient -> save(patient) }
  }
}