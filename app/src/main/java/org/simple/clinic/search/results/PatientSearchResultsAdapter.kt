package org.simple.clinic.search.results

import android.support.v7.widget.RecyclerView
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import io.reactivex.subjects.PublishSubject
import kotterknife.bindView
import org.simple.clinic.R
import org.simple.clinic.patient.PatientSearchResult
import org.simple.clinic.widgets.UiEvent
import org.threeten.bp.LocalDate
import org.threeten.bp.LocalDateTime
import org.threeten.bp.Period
import org.threeten.bp.ZoneOffset.UTC
import org.threeten.bp.format.DateTimeFormatter
import java.util.Locale
import javax.inject.Inject

private val DATE_OF_BIRTH_FORMATTER = DateTimeFormatter.ofPattern("d-MM-yyyy", Locale.ENGLISH)

class PatientSearchResultsAdapter @Inject constructor(
    private val phoneObfuscator: PhoneNumberObfuscator
) : RecyclerView.Adapter<PatientSearchResultsAdapter.ViewHolder>() {

  private var patients: List<PatientSearchResult> = listOf()

  val itemClicks: PublishSubject<UiEvent> = PublishSubject.create<UiEvent>()

  fun updateAndNotifyChanges(patients: List<PatientSearchResult>) {
    this.patients = patients
    notifyDataSetChanged()
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val inflater = LayoutInflater.from(parent.context)
    val holder = ViewHolder(inflater.inflate(R.layout.list_patient_search, parent, false))
    holder.setClickListener(itemClicks)
    return holder
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    holder.searchResult = patients[position]
    holder.render(phoneObfuscator)
  }

  override fun getItemCount(): Int {
    return patients.size
  }

  class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    private val genderImageView by bindView<ImageView>(R.id.patientsearchresult_item_gender)
    private val titleTextView by bindView<TextView>(R.id.patientsearchresult_item_title)
    private val addressTextView by bindView<TextView>(R.id.patientsearchresult_item_address)
    private val dateOfBirthTextView by bindView<TextView>(R.id.patientsearchresult_item_dateofbirth)
    private val phoneNumberTextView by bindView<TextView>(R.id.patientsearchresult_item_phone)
    private val lastBpDateTextView by bindView<TextView>(R.id.patientsearchresults_item_last_bp)
    private val ageTextView by bindView<TextView>(R.id.patientsearch_item_age)

    lateinit var searchResult: PatientSearchResult

    fun setClickListener(itemClicks: PublishSubject<UiEvent>) {
      itemView.setOnClickListener {
        itemClicks.onNext(PatientSearchResultClicked(searchResult))
      }
    }

    fun render(phoneObfuscator: PhoneNumberObfuscator) {
      genderImageView.setImageResource(searchResult.gender.displayIconRes)

      val resources = itemView.resources
      titleTextView.text = resources.getString(
          R.string.patientsearchresults_item_name_with_gender,
          searchResult.fullName,
          resources.getString(searchResult.gender.displayLetterRes))

      val address = searchResult.address
      if (address.colonyOrVillage.isNullOrEmpty()) {
        addressTextView.text = searchResult.address.district
      } else {
        addressTextView.text = resources.getString(
            R.string.patientsearchresults_item_address_with_colony_and_district,
            searchResult.address.colonyOrVillage,
            searchResult.address.district)
      }

      val dateOfBirth = searchResult.dateOfBirth
      if (dateOfBirth == null) {
        dateOfBirthTextView.visibility = View.GONE
      } else {
        dateOfBirthTextView.visibility = View.VISIBLE
        dateOfBirthTextView.text = DATE_OF_BIRTH_FORMATTER.format(dateOfBirth)
      }

      val phoneNumber = searchResult.phoneNumber
      if (phoneNumber.isNullOrBlank()) {
        phoneNumberTextView.visibility = View.GONE
      } else {
        phoneNumberTextView.visibility = View.VISIBLE
        phoneNumberTextView.text = phoneObfuscator.obfuscate(phoneNumber!!)
      }

      // TODO: Join BP table with patient search query.
      val lastBpDate: LocalDate? = null
      val lastBpFacilityName: String? = null

      if (lastBpDate == null) {
        lastBpDateTextView.visibility = View.GONE
      } else {
        lastBpDateTextView.visibility = View.VISIBLE
        val formattedLastBpDate = DATE_OF_BIRTH_FORMATTER.format(lastBpDate)
        lastBpDateTextView.text = resources.getString(
            R.string.patientsearchresults_item_last_bp_date_with_facility,
            formattedLastBpDate,
            lastBpFacilityName)
      }

      val age = searchResult.age
      if (age == null) {
        val years = Period.between(searchResult.dateOfBirth, LocalDate.now()).years
        ageTextView.text = years.toString()

      } else {
        val ageUpdatedAt = LocalDateTime.ofInstant(age.updatedAt, UTC)
        val updatedAtLocalDate = LocalDate.of(ageUpdatedAt.year, ageUpdatedAt.month, ageUpdatedAt.dayOfMonth)
        val yearsSinceThen = Period.between(updatedAtLocalDate, LocalDate.now()).years

        val oldAge = age.value
        val currentAge = oldAge + yearsSinceThen
        ageTextView.text = currentAge.toString()
      }
    }
  }
}
