package com.example.furbetterchoice.api

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class PetLifeStageCategory {
    GROWTH,
    ADULT,
    SENIOR
}

enum class PetGrowthSubStage {
    EARLY_UNDER_14_WEEKS,
    LATE_14_WEEKS_PLUS,
    UNSPECIFIED
}

enum class ProductSpeciesTarget {
    CAT,
    DOG,
    ANY
}

data class PetLifeStageClassification(
    val isCat: Boolean,
    val isDog: Boolean,
    val category: PetLifeStageCategory,
    val growthSubStage: PetGrowthSubStage,
    val displayLabel: String,
    val ageDisplay: String
)

data class ProductPetFilterSpec(
    val speciesTarget: ProductSpeciesTarget,
    val allowsGrowth: Boolean,
    val allowsAdult: Boolean,
    val allowsSenior: Boolean,
    val requiresEarlyGrowthOnly: Boolean = false,
    val requiresLateGrowthOnly: Boolean = false,
    val isAllLifeStages: Boolean,
    val filterBadgeLabel: String,
    val emptyMatchHint: String
)

object PetAssessmentFilter {

    fun calculateAgeInMonths(birthday: String?): Int? {
        if (birthday.isNullOrBlank()) return null
        return try {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { isLenient = false }
            val birthDate = sdf.parse(birthday.trim()) ?: return null
            val now = Calendar.getInstance()
            val birth = Calendar.getInstance().apply { time = birthDate }
            if (birth.after(now)) return 0
            var months = (now.get(Calendar.YEAR) - birth.get(Calendar.YEAR)) * 12 +
                (now.get(Calendar.MONTH) - birth.get(Calendar.MONTH))
            if (now.get(Calendar.DAY_OF_MONTH) < birth.get(Calendar.DAY_OF_MONTH)) {
                months--
            }
            months.coerceAtLeast(0)
        } catch (_: Exception) {
            null
        }
    }

    fun isPetYoungGrowth(birthday: String?, fallbackAgeYears: Long?): Boolean {
        val months = calculateAgeInMonths(birthday)
        if (months != null) return months < 12
        return fallbackAgeYears != null && fallbackAgeYears == 0L
    }

    fun classifyPet(pet: Pet): PetLifeStageClassification {
        val typeLower = pet.type.trim().lowercase(Locale.ROOT)
        val isCat = typeLower.contains("cat") || typeLower.contains("feline")
        val isDog = typeLower.contains("dog") || typeLower.contains("canine")

        val months = calculateAgeInMonths(pet.birthday)
        val weeks = NutritionEnergyHelper.calculateAgeInWeeks(pet.birthday)
        val years = NutritionEnergyHelper.calculateAgeInYears(pet.birthday, pet.age)

        val category = when {
            months != null -> when {
                months < 12 -> PetLifeStageCategory.GROWTH
                months >= 84 -> PetLifeStageCategory.SENIOR
                else -> PetLifeStageCategory.ADULT
            }
            years != null -> when {
                years == 0L -> PetLifeStageCategory.GROWTH
                years >= 7L -> PetLifeStageCategory.SENIOR
                else -> PetLifeStageCategory.ADULT
            }
            else -> PetLifeStageCategory.ADULT
        }

        val growthSubStage = if (category == PetLifeStageCategory.GROWTH && weeks != null) {
            if (weeks < 14L) PetGrowthSubStage.EARLY_UNDER_14_WEEKS
            else PetGrowthSubStage.LATE_14_WEEKS_PLUS
        } else {
            PetGrowthSubStage.UNSPECIFIED
        }

        val displayLabel = when (category) {
            PetLifeStageCategory.GROWTH -> if (isCat) "Kitten" else "Puppy"
            PetLifeStageCategory.ADULT -> "Adult"
            PetLifeStageCategory.SENIOR -> "Senior"
        }

        val ageDisplay = when {
            weeks != null && weeks < 14L -> if (weeks <= 0L) "<1 wk" else if (weeks == 1L) "1 wk" else "$weeks wks"
            months != null && months < 12 -> if (months <= 1) "1 mo" else "$months mos"
            years != null && years == 0L -> "<1 yr"
            years != null && years == 1L -> "1 yr"
            years != null -> "$years yrs"
            else -> ""
        }

        return PetLifeStageClassification(
            isCat = isCat,
            isDog = isDog,
            category = category,
            growthSubStage = growthSubStage,
            displayLabel = displayLabel,
            ageDisplay = ageDisplay
        )
    }

    fun buildFilterSpec(product: Product): ProductPetFilterSpec {
        val rawSpecies = product.pet.ifBlank { product.species }.trim().lowercase(Locale.ROOT)
        val rawStage = product.lifeStage.trim()
        val lowerStage = rawStage.lowercase(Locale.ROOT)

        val mentionsCat = rawSpecies.contains("cat") || rawSpecies.contains("feline")
        val mentionsDog = rawSpecies.contains("dog") || rawSpecies.contains("canine")
        val speciesTarget = when {
            mentionsCat && !mentionsDog -> ProductSpeciesTarget.CAT
            mentionsDog && !mentionsCat -> ProductSpeciesTarget.DOG
            else -> ProductSpeciesTarget.ANY
        }

        val isUnspecifiedOrAll = rawStage.isEmpty() ||
            lowerStage == "not specified" ||
            lowerStage == "unknown" ||
            lowerStage == "n/a" ||
            lowerStage == "none" ||
            lowerStage.contains("all life stages") ||
            lowerStage.contains("all stages")

        if (isUnspecifiedOrAll) {
            val isAllExplicit = lowerStage.contains("all life stages") || lowerStage.contains("all stages")
            val badge = when (speciesTarget) {
                ProductSpeciesTarget.CAT -> if (isAllExplicit) "Cat · All Stages" else "Cat"
                ProductSpeciesTarget.DOG -> if (isAllExplicit) "Dog · All Stages" else "Dog"
                ProductSpeciesTarget.ANY -> if (isAllExplicit) "All Stages" else "All Pets"
            }
            val emptyHint = when (speciesTarget) {
                ProductSpeciesTarget.CAT -> "No cat profiles in My Pets. Showing standard Cat guidelines."
                ProductSpeciesTarget.DOG -> "No dog profiles in My Pets. Showing standard Dog guidelines."
                ProductSpeciesTarget.ANY -> "No matching pet profiles found."
            }
            return ProductPetFilterSpec(
                speciesTarget = speciesTarget,
                allowsGrowth = true,
                allowsAdult = true,
                allowsSenior = true,
                isAllLifeStages = true,
                filterBadgeLabel = badge,
                emptyMatchHint = emptyHint
            )
        }

        val hasEarlyToken = lowerStage.contains("early") ||
            lowerStage.contains("<14") ||
            lowerStage.contains("< 14") ||
            lowerStage.contains("under 14") ||
            lowerStage.contains("starter") ||
            lowerStage.contains("weaning")

        val hasLateToken = lowerStage.contains("late") ||
            lowerStage.contains("14+") ||
            lowerStage.contains(">=14") ||
            lowerStage.contains("≥14") ||
            lowerStage.contains("over 14")

        val hasGrowthToken = hasEarlyToken ||
            hasLateToken ||
            lowerStage.contains("puppy") ||
            lowerStage.contains("kitten") ||
            lowerStage.contains("growth") ||
            lowerStage.contains("junior")

        val hasAdultToken = lowerStage.contains("adult") || lowerStage.contains("maintenance")
        val hasSeniorToken = lowerStage.contains("senior") ||
            lowerStage.contains("mature") ||
            lowerStage.contains("aging") ||
            lowerStage.contains("7+") ||
            lowerStage.contains("11+")

        val resolvedAnyStage = hasGrowthToken || hasAdultToken || hasSeniorToken
        val allowsGrowth = if (resolvedAnyStage) hasGrowthToken else true
        val allowsAdult = if (resolvedAnyStage) hasAdultToken else true
        val allowsSenior = if (resolvedAnyStage) hasSeniorToken else true

        val requiresEarlyOnly = speciesTarget == ProductSpeciesTarget.DOG && hasEarlyToken && !hasLateToken
        val requiresLateOnly = speciesTarget == ProductSpeciesTarget.DOG && hasLateToken && !hasEarlyToken

        val effectiveSpeciesTarget = when {
            speciesTarget != ProductSpeciesTarget.ANY -> speciesTarget
            lowerStage.contains("kitten") && !lowerStage.contains("puppy") -> ProductSpeciesTarget.CAT
            lowerStage.contains("puppy") && !lowerStage.contains("kitten") -> ProductSpeciesTarget.DOG
            else -> ProductSpeciesTarget.ANY
        }

        val stageParts = buildList {
            if (allowsGrowth) {
                add(
                    when {
                        effectiveSpeciesTarget == ProductSpeciesTarget.CAT -> "Kitten"
                        requiresEarlyOnly -> "Puppy (<14 wks)"
                        requiresLateOnly -> "Puppy (≥14 wks)"
                        else -> "Puppy"
                    }
                )
            }
            if (allowsAdult) add("Adult")
            if (allowsSenior) add("Senior")
        }

        val speciesSuffix = when (effectiveSpeciesTarget) {
            ProductSpeciesTarget.CAT -> if (stageParts.size == 1 && stageParts.first() == "Kitten") "" else " Cat"
            ProductSpeciesTarget.DOG -> if (stageParts.size == 1 && stageParts.first().startsWith("Puppy")) "" else " Dog"
            ProductSpeciesTarget.ANY -> ""
        }

        val badgeLabel = if (stageParts.isNotEmpty()) {
            "${stageParts.joinToString(" / ")}$speciesSuffix"
        } else {
            rawStage
        }

        val emptyHint = "No matching $badgeLabel profiles in My Pets. Showing standard $badgeLabel guidelines."

        return ProductPetFilterSpec(
            speciesTarget = effectiveSpeciesTarget,
            allowsGrowth = allowsGrowth,
            allowsAdult = allowsAdult,
            allowsSenior = allowsSenior,
            requiresEarlyGrowthOnly = requiresEarlyOnly,
            requiresLateGrowthOnly = requiresLateOnly,
            isAllLifeStages = false,
            filterBadgeLabel = badgeLabel,
            emptyMatchHint = emptyHint
        )
    }

    fun filterPetsForProduct(pets: List<Pet>, product: Product): List<Pet> {
        val spec = buildFilterSpec(product)
        return pets.filter { pet ->
            val info = classifyPet(pet)
            val speciesMatches = when (spec.speciesTarget) {
                ProductSpeciesTarget.CAT -> info.isCat
                ProductSpeciesTarget.DOG -> info.isDog
                ProductSpeciesTarget.ANY -> true
            }
            if (!speciesMatches) return@filter false

            val stageMatches = when (info.category) {
                PetLifeStageCategory.GROWTH -> {
                    if (!spec.allowsGrowth) false
                    else if (spec.requiresEarlyGrowthOnly) {
                        info.growthSubStage == PetGrowthSubStage.EARLY_UNDER_14_WEEKS ||
                            info.growthSubStage == PetGrowthSubStage.UNSPECIFIED
                    } else if (spec.requiresLateGrowthOnly) {
                        info.growthSubStage == PetGrowthSubStage.LATE_14_WEEKS_PLUS ||
                            info.growthSubStage == PetGrowthSubStage.UNSPECIFIED
                    } else {
                        true
                    }
                }
                PetLifeStageCategory.ADULT -> spec.allowsAdult
                PetLifeStageCategory.SENIOR -> spec.allowsSenior
            }
            stageMatches
        }
    }
}
