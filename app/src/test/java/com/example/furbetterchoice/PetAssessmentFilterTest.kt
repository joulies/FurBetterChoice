package com.example.furbetterchoice

import com.example.furbetterchoice.api.AafcoAssessmentEngine
import com.example.furbetterchoice.api.AafcoLifeStageCategory
import com.example.furbetterchoice.api.FediafLifeStage
import com.example.furbetterchoice.api.NutrientValue
import com.example.furbetterchoice.api.NutritionAssessmentEngine
import com.example.furbetterchoice.api.Pet
import com.example.furbetterchoice.api.PetAssessmentFilter
import com.example.furbetterchoice.api.PetLifeStageCategory
import com.example.furbetterchoice.api.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class PetAssessmentFilterTest {

    private fun birthdayMonthsAgo(months: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -months)
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    private fun birthdayWeeksAgo(weeks: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -(weeks * 7))
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(cal.time)
    }

    private fun sampleMixedPets(): List<Pet> = listOf(
        Pet(petId = "dog_early", petName = "Tiny", type = "Dog", breed = "Poodle", birthday = birthdayWeeksAgo(8)),
        Pet(petId = "dog_late", petName = "Rex", type = "Dog", breed = "Labrador", birthday = birthdayMonthsAgo(6)),
        Pet(petId = "dog_adult", petName = "Buddy", type = "Dog", breed = "Beagle", birthday = birthdayMonthsAgo(36)),
        Pet(petId = "dog_senior", petName = "Oldie", type = "Dog", breed = "Beagle", birthday = birthdayMonthsAgo(96)),
        Pet(petId = "cat_kitten", petName = "Mochi", type = "Cat", breed = "Domestic", birthday = birthdayMonthsAgo(4)),
        Pet(petId = "cat_adult", petName = "Luna", type = "Cat", breed = "Siamese", birthday = birthdayMonthsAgo(30)),
        Pet(petId = "cat_senior", petName = "Shadow", type = "Cat", breed = "Persian", birthday = birthdayMonthsAgo(108))
    )

    @Test
    fun dogAdultProductDisplaysOnlyAdultDogs() {
        val product = Product(productId = "P1", pet = "Dog", lifeStage = "Adult")
        val filtered = PetAssessmentFilter.filterPetsForProduct(sampleMixedPets(), product)

        assertEquals(listOf("dog_adult"), filtered.map { it.petId })
    }

    @Test
    fun catKittenProductDisplaysOnlyKittens() {
        val product = Product(productId = "P2", pet = "Cat", lifeStage = "Kitten")
        val filtered = PetAssessmentFilter.filterPetsForProduct(sampleMixedPets(), product)

        assertEquals(listOf("cat_kitten"), filtered.map { it.petId })
    }

    @Test
    fun catAllLifeStagesDisplaysCatsAcrossDifferentLifeStages() {
        val product = Product(productId = "P3", pet = "Cat", lifeStage = "All Life Stages")
        val filtered = PetAssessmentFilter.filterPetsForProduct(sampleMixedPets(), product)

        assertEquals(listOf("cat_kitten", "cat_adult", "cat_senior"), filtered.map { it.petId })
    }

    @Test
    fun dogEarlyAndLateGrowthProductsFilterPuppiesByWeeks() {
        val earlyProduct = Product(productId = "P4", pet = "Dog", lifeStage = "Early Growth (<14 wks)")
        val lateProduct = Product(productId = "P5", pet = "Dog", lifeStage = "Late Growth (≥14 wks)")

        val earlyFiltered = PetAssessmentFilter.filterPetsForProduct(sampleMixedPets(), earlyProduct)
        val lateFiltered = PetAssessmentFilter.filterPetsForProduct(sampleMixedPets(), lateProduct)

        assertEquals(listOf("dog_early"), earlyFiltered.map { it.petId })
        assertEquals(listOf("dog_late"), lateFiltered.map { it.petId })
    }

    @Test
    fun seniorProductDisplaysOnlySeniorOfMatchingSpecies() {
        val seniorCatProduct = Product(productId = "P6", pet = "Cat", lifeStage = "Senior")
        val filtered = PetAssessmentFilter.filterPetsForProduct(sampleMixedPets(), seniorCatProduct)

        assertEquals(listOf("cat_senior"), filtered.map { it.petId })
    }

    @Test
    fun ageFallbackWithoutBirthdayClassifiesZeroAsGrowthAndSevenPlusAsSenior() {
        val puppy = Pet(petId = "p0", petName = "Pup", type = "Dog", age = 0, birthday = "")
        val adult = Pet(petId = "p3", petName = "Ad", type = "Dog", age = 3, birthday = "")
        val senior = Pet(petId = "p8", petName = "Sen", type = "Dog", age = 8, birthday = "")

        assertEquals(PetLifeStageCategory.GROWTH, PetAssessmentFilter.classifyPet(puppy).category)
        assertEquals(PetLifeStageCategory.ADULT, PetAssessmentFilter.classifyPet(adult).category)
        assertEquals(PetLifeStageCategory.SENIOR, PetAssessmentFilter.classifyPet(senior).category)
    }

    @Test
    fun allLifeStagesProductAdaptsFediafAndAafcoToSelectedCatLifeStage() {
        val catAllStages = Product(
            productId = "P_CAT_ALL",
            pet = "Cat",
            lifeStage = "All Life Stages",
            nutrition = mapOf(
                "crudeProtein" to NutrientValue("Crude Protein", 30.0, "%"),
                "crudeFat" to NutrientValue("Crude Fat", 12.0, "%"),
                "calcium" to NutrientValue("Calcium", 1.1, "%"),
                "phosphorus" to NutrientValue("Phosphorus", 0.9, "%"),
                "moisture" to NutrientValue("Moisture", 10.0, "%")
            )
        )

        val kittenBirthday = birthdayMonthsAgo(4)
        val adultBirthday = birthdayMonthsAgo(30)
        val seniorBirthday = birthdayMonthsAgo(108)

        // Default (no pet selected) -> Growth
        assertEquals(
            FediafLifeStage.CAT_GROWTH,
            NutritionAssessmentEngine.assess(catAllStages).resolvedLifeStage
        )
        assertEquals(
            AafcoLifeStageCategory.KITTEN,
            AafcoAssessmentEngine.assess(catAllStages).lifeStageCategory
        )

        // Kitten selected -> Growth
        assertEquals(
            FediafLifeStage.CAT_GROWTH,
            NutritionAssessmentEngine.assess(catAllStages, userPetType = "Cat", userPetBirthday = kittenBirthday).resolvedLifeStage
        )
        assertEquals(
            AafcoLifeStageCategory.KITTEN,
            AafcoAssessmentEngine.assess(catAllStages, userPetType = "Cat", userPetBirthday = kittenBirthday).lifeStageCategory
        )

        // Adult Cat selected -> Adult
        assertEquals(
            FediafLifeStage.CAT_ADULT_100,
            NutritionAssessmentEngine.assess(catAllStages, userPetType = "Cat", userPetBirthday = adultBirthday).resolvedLifeStage
        )
        assertEquals(
            AafcoLifeStageCategory.ADULT_CAT,
            AafcoAssessmentEngine.assess(catAllStages, userPetType = "Cat", userPetBirthday = adultBirthday).lifeStageCategory
        )

        // Senior Cat selected -> Senior (mapped to Adult)
        val fediafSenior = NutritionAssessmentEngine.assess(catAllStages, userPetType = "Cat", userPetBirthday = seniorBirthday)
        val aafcoSenior = AafcoAssessmentEngine.assess(catAllStages, userPetType = "Cat", userPetBirthday = seniorBirthday)
        assertEquals(FediafLifeStage.CAT_SENIOR, fediafSenior.resolvedLifeStage)
        assertTrue(fediafSenior.isSeniorMappedToAdult)
        assertEquals(AafcoLifeStageCategory.SENIOR_CAT, aafcoSenior.lifeStageCategory)
        assertTrue(aafcoSenior.isSeniorMappedToAdultMaintenance)
    }
}
