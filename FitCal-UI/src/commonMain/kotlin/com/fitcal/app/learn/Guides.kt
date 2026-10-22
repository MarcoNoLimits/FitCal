package com.fitcal.app.learn

import com.fitcal.app.plan.BodyStats
import com.fitcal.app.plan.TargetsMode
import com.fitcal.app.plan.currentPlanTargets
import com.fitcal.app.plan.formatKg
import com.fitcal.app.plan.resolvedTargetsMode
import com.fitcal.shared.model.UserProfile
import kotlin.math.roundToInt

/*
 * In-app nutrition guides (Library → Guides, and "?" links where questions come up).
 *
 * Editorial rules, because this is health content in an 18+ app with eating-disorder
 * safeguards: facts and trade-offs only; no "good"/"bad" foods; no pressure to lose weight;
 * every guide names its sources and ends with the not-medical-advice note (GuideScreen).
 * Content is bundled so it works offline. Owner review is required before release.
 */

data class GuideSection(
    val heading: String? = null,
    val paragraphs: List<String> = emptyList(),
    val bullets: List<String> = emptyList(),
)

data class Guide(
    val id: String,
    val title: String,
    val summary: String,
    val sections: List<GuideSection>,
    val sources: List<String>,
    val related: List<String> = emptyList(),
    /** No banner ad on this guide (sensitive topics such as health conditions and eating disorders). */
    val adFree: Boolean = false,
    /** A line using the user's own numbers, when they have them. */
    val personalNote: (UserProfile) -> String? = { null },
) {
    /** Reading time at ~200 words a minute, at least 1 minute. */
    val readMinutes: Int
        get() {
            val words = sections.sumOf { s ->
                (listOfNotNull(s.heading) + s.paragraphs + s.bullets).sumOf { it.split(' ').size }
            }
            return maxOf(1, (words / 200f).roundToInt())
        }
}

object GuideIds {
    const val CALORIES = "calories-vs-kcal"
    const val ENERGY_BALANCE = "energy-balance"
    const val TRACKING_WEIGHT = "tracking-weight"
    const val PROTEIN = "protein"
    const val CARBS = "carbohydrates"
    const val FAT = "fat"
    const val SUGAR = "sugar"
    const val PHOTO_ESTIMATES = "photo-estimates"
    const val PROFESSIONAL_HELP = "professional-help"
}

private fun UserProfile.planTargetsIfAny() =
    if (resolvedTargetsMode() == TargetsMode.PLAN) currentPlanTargets() else null

private fun withThousands(n: Int): String = n.toString().reversed().chunked(3).joinToString(",").reversed()

object Guides {

    val all: List<Guide> = listOf(
        Guide(
            id = GuideIds.CALORIES,
            title = "Calories vs kcal",
            summary = "What the number on a label really measures, and where it comes from.",
            sections = listOf(
                GuideSection(
                    paragraphs = listOf(
                        "A calorie is a unit of energy. When people talk about food they say \"calories\" but mean kilocalories (kcal). Some labels write it as \"Calorie\" with a capital C to show the difference.",
                    ),
                    bullets = listOf(
                        "1 kcal = 1,000 small calories",
                        "1,000 kcal = 1,000,000 small calories",
                        "In everyday speech, 1,000 kcal is called \"1,000 calories\"",
                    ),
                ),
                GuideSection(
                    paragraphs = listOf(
                        "So a 500-calorie meal on a food label or in FitCal is 500 kcal. That equals 500,000 small calories, but nobody counts food that way.",
                        "Some countries also show kilojoules (kJ). 1 kcal = 4.184 kJ, so a 2,000 kcal day is about 8,370 kJ. EU labels show both.",
                    ),
                ),
                GuideSection(
                    heading = "Where the energy comes from",
                    paragraphs = listOf("Labels use standard averages for how much energy each nutrient provides:"),
                    bullets = listOf(
                        "Protein: 4 kcal per gram",
                        "Carbohydrate: 4 kcal per gram",
                        "Fat: 9 kcal per gram",
                        "Alcohol: 7 kcal per gram",
                        "Fibre: about 2 kcal per gram",
                    ),
                ),
                GuideSection(
                    paragraphs = listOf(
                        "That's why fat-rich foods such as oils and nuts are energy-dense: a tablespoon of oil (about 14 g) is roughly 120 kcal. It also explains why a meal's calories roughly equal protein × 4 + carbs × 4 + fat × 9.",
                        "These are averages. Real absorption varies with the food, how it's cooked and your own digestion, so treat every calorie number, on a label or from a photo, as a good estimate rather than an exact measurement.",
                    ),
                ),
            ),
            sources = listOf(
                "EU Regulation 1169/2011 on food information to consumers, Annex XIV (energy conversion factors)",
                "U.S. FDA, Nutrition Facts label guidance",
            ),
            related = listOf(GuideIds.ENERGY_BALANCE, GuideIds.PHOTO_ESTIMATES),
            personalNote = { p ->
                p.planTargetsIfAny()?.let { t ->
                    "Your daily target of ${withThousands(t.calories)} kcal is about ${withThousands((t.calories * 4.184f).roundToInt())} kJ."
                }
            },
        ),

        Guide(
            id = GuideIds.ENERGY_BALANCE,
            title = "Energy balance",
            summary = "Maintenance, deficit and surplus, and why the 7,700 kcal rule is only a rule of thumb.",
            sections = listOf(
                GuideSection(
                    heading = "Maintenance",
                    paragraphs = listOf(
                        "Your body uses energy all day: to keep you alive at rest (your basal metabolic rate), to move, and to digest food. Together that's your maintenance: the calories that keep your weight roughly stable.",
                        "FitCal estimates it from your height, weight, age, sex and activity level (the Mifflin-St Jeor equation). For most people such estimates land within about 10% of the true value, so think of it as a starting point.",
                    ),
                ),
                GuideSection(
                    heading = "Deficit and surplus",
                    bullets = listOf(
                        "Eat less than maintenance (a deficit) and your body makes up the difference from stored energy, so weight tends to go down.",
                        "Eat more (a surplus) and the extra is stored, so weight tends to go up. With enough protein and strength training, part of that can be muscle.",
                        "Eat about the same and weight tends to stay put, even if it moves day to day.",
                    ),
                ),
                GuideSection(
                    heading = "The 7,700 kcal rule",
                    paragraphs = listOf(
                        "A common shortcut says 1 kg of body fat holds about 7,700 kcal, so a 550 kcal daily deficit loses about 0.5 kg a week. It's useful for planning, but real results drift from it:",
                    ),
                    bullets = listOf(
                        "Early weight loss includes water and stored carbohydrate, so the first week or two often move faster.",
                        "As you get lighter, maintenance drops, so the same intake becomes a smaller deficit and progress slows.",
                        "Daily water swings can hide fat loss for a week or more.",
                    ),
                ),
                GuideSection(
                    paragraphs = listOf(
                        "This is why FitCal's plan offers a check-in: updating your weight now and then lets it recalculate your targets instead of relying on the first estimate forever.",
                    ),
                ),
            ),
            sources = listOf(
                "Mifflin MD et al. (1990), A new predictive equation for resting energy expenditure, Am J Clin Nutr",
                "Hall KD et al. (2011), Quantification of the effect of energy imbalance on bodyweight, The Lancet",
                "NIH Body Weight Planner",
            ),
            related = listOf(GuideIds.TRACKING_WEIGHT, GuideIds.CALORIES, GuideIds.PROTEIN),
            personalNote = { p ->
                p.planTargetsIfAny()?.let { t ->
                    val diff = t.calories - t.maintenance
                    when {
                        diff < -20 -> "Your maintenance is about ${withThousands(t.maintenance)} kcal. Your target of ${withThousands(t.calories)} kcal is a ${withThousands(-diff)} kcal daily deficit, about ${formatKg(t.kgPerWeek)} kg a week."
                        diff > 20 -> "Your maintenance is about ${withThousands(t.maintenance)} kcal. Your target of ${withThousands(t.calories)} kcal is a ${withThousands(diff)} kcal daily surplus, about ${formatKg(t.kgPerWeek)} kg a week."
                        else -> "Your maintenance is about ${withThousands(t.maintenance)} kcal, and your target matches it."
                    }
                }
            },
        ),

        Guide(
            id = GuideIds.TRACKING_WEIGHT,
            title = "Tracking your weight",
            summary = "Why the scale jumps around, and how to read the trend instead.",
            sections = listOf(
                GuideSection(
                    paragraphs = listOf(
                        "Body weight can move by 1 to 2 kg from one day to the next without any change in body fat. The usual causes:",
                    ),
                    bullets = listOf(
                        "Water: salty or carbohydrate-heavy meals make your body hold more water for a day or two.",
                        "Food still being digested.",
                        "Hormonal cycles, which can shift weight for several days.",
                        "Hard training, which can cause temporary water retention in muscles.",
                    ),
                ),
                GuideSection(
                    heading = "Weigh in a consistent way",
                    bullets = listOf(
                        "Same time of day: first thing in the morning, after the bathroom and before eating or drinking.",
                        "Same scale, on a hard, flat floor.",
                        "Similar clothing, or none.",
                    ),
                ),
                GuideSection(
                    heading = "Look at the trend",
                    paragraphs = listOf(
                        "One reading tells you little. Compare weekly averages, and judge progress over two to four weeks. A week with no change, or a small rise, is normal and not a sign that something is wrong.",
                        "A steady pace is easier to keep. Health services commonly suggest losing about 0.5 to 1 kg a week at most; FitCal's paces stay within about 1% of body weight a week.",
                    ),
                ),
                GuideSection(
                    paragraphs = listOf(
                        "If weighing yourself makes you anxious or affects your mood, it's fine to weigh less often, or not at all, and focus on how you feel, energy and how clothes fit.",
                    ),
                ),
            ),
            sources = listOf(
                "NHS, Losing weight: getting started",
                "Hall KD et al. (2011), The Lancet",
            ),
            related = listOf(GuideIds.ENERGY_BALANCE, GuideIds.PROFESSIONAL_HELP),
        ),

        Guide(
            id = GuideIds.PROTEIN,
            title = "Protein",
            summary = "What it does, how much is typical, and the trade-offs of eating more.",
            sections = listOf(
                GuideSection(
                    paragraphs = listOf(
                        "Protein supplies the amino acids your body uses to build and repair muscle and other tissue, and to make enzymes and hormones. It provides 4 kcal per gram.",
                    ),
                ),
                GuideSection(
                    heading = "How much",
                    bullets = listOf(
                        "The general adult reference intake is about 0.8 g per kg of body weight a day: enough to avoid deficiency for most people.",
                        "Active people, and people eating less to lose weight, often benefit from more, commonly 1.2 to 2.0 g per kg, to help keep muscle.",
                        "FitCal's plan uses 1.6 g/kg to maintain, 1.8 to gain and 2.0 while losing, based on a healthy-range body weight so the number stays sensible for larger bodies.",
                    ),
                ),
                GuideSection(
                    heading = "Benefits",
                    bullets = listOf(
                        "Helps keep and build muscle, especially with strength training.",
                        "Tends to be filling, which can make a calorie target easier to stick to.",
                    ),
                ),
                GuideSection(
                    heading = "Trade-offs",
                    bullets = listOf(
                        "Very high intakes can crowd out fibre-rich foods such as vegetables, fruit and whole grains.",
                        "Processed meats (bacon, ham, sausages) are linked to a higher bowel-cancer risk, so it helps to vary your sources.",
                        "If you have kidney disease, ask your doctor before raising protein intake.",
                    ),
                ),
                GuideSection(
                    heading = "Common sources",
                    paragraphs = listOf("Fish, poultry, eggs, dairy and yogurt, beans, lentils, tofu and other soy foods, nuts and seeds."),
                ),
            ),
            sources = listOf(
                "WHO/FAO/UNU (2007), Protein and amino acid requirements in human nutrition",
                "Jäger R et al. (2017), ISSN position stand: protein and exercise",
                "IARC/WHO (2015), Carcinogenicity of consumption of red and processed meat",
            ),
            related = listOf(GuideIds.CARBS, GuideIds.FAT, GuideIds.ENERGY_BALANCE),
            personalNote = { p ->
                val body = BodyStats.from(p)
                val t = p.planTargetsIfAny()
                if (body != null && t != null) {
                    val ref = minOf(body.weightKg, body.weightAtBmi(25f))
                    "At your weight, 1.2 to 2.0 g/kg is about ${(ref * 1.2f).roundToInt()} to ${(ref * 2.0f).roundToInt()} g a day. Your plan's target is ${t.proteinG} g."
                } else null
            },
        ),

        Guide(
            id = GuideIds.SUGAR,
            title = "Sugar",
            summary = "What the sugar number in FitCal means and where the 50 g guide comes from.",
            sections = listOf(
                GuideSection(
                    paragraphs = listOf(
                        "Sugar is a carbohydrate, so it is already part of your carbs. FitCal shows it on its own because many people want to keep an eye on it.",
                    ),
                ),
                GuideSection(
                    heading = "The 50 g guide",
                    paragraphs = listOf(
                        "The WHO recommends keeping free sugars under 10% of daily energy, which is about 50 g a day for most adults, and ideally under 5% (about 25 g). Free sugars are sugars added to food and drinks, plus those in honey, syrups and fruit juice.",
                    ),
                ),
                GuideSection(
                    heading = "Why FitCal's number can look high",
                    paragraphs = listOf(
                        "FitCal estimates total sugars, so it also counts the sugar in whole fruit, vegetables and milk. Those don't count towards the WHO limit. A day with plenty of fruit can pass 50 g without any added sugar, and that's fine.",
                    ),
                ),
                GuideSection(
                    heading = "Easy places to look",
                    bullets = listOf(
                        "Soft drinks, energy drinks and fruit juice.",
                        "Sweetened yoghurts, cereals and cereal bars.",
                        "Sauces such as ketchup and some ready meals.",
                    ),
                ),
            ),
            sources = listOf(
                "WHO (2015), Guideline: sugars intake for adults and children",
            ),
            related = listOf(GuideIds.CARBS),
        ),

        Guide(
            id = GuideIds.CARBS,
            title = "Carbohydrates",
            summary = "Your body's main fuel, why fibre matters, and how sugar fits in.",
            sections = listOf(
                GuideSection(
                    paragraphs = listOf(
                        "Carbohydrates are starches, sugars and fibre. Starches and sugars are your body's main fuel, especially for the brain and for hard exercise, and provide 4 kcal per gram.",
                    ),
                ),
                GuideSection(
                    heading = "Fibre",
                    paragraphs = listOf(
                        "Fibre is the part of plant foods you don't fully digest. It supports digestion and helps you feel full. Many adults eat less than the commonly recommended 25 to 30 g a day.",
                    ),
                    bullets = listOf(
                        "Good sources: whole grains, oats, beans and lentils, vegetables, fruit, nuts and seeds.",
                    ),
                ),
                GuideSection(
                    heading = "Sugar",
                    paragraphs = listOf(
                        "The WHO recommends keeping free sugars (added sugars plus those in honey, syrups and fruit juice) under 10% of daily energy, and ideally under 5%. Sugar in whole fruit and in milk doesn't count towards that limit.",
                    ),
                ),
                GuideSection(
                    heading = "Do you need to cut carbs?",
                    paragraphs = listOf(
                        "No. Studies comparing low-carb and low-fat diets with similar calories find similar weight loss on average. The best split is one you can keep up. Some people find fewer carbs helps them eat less; others feel and train better with more.",
                    ),
                ),
            ),
            sources = listOf(
                "WHO (2015), Guideline: sugars intake for adults and children",
                "EFSA (2010), Dietary reference values for carbohydrates and dietary fibre",
                "Gardner CD et al. (2018), DIETFITS randomized clinical trial, JAMA",
            ),
            related = listOf(GuideIds.PROTEIN, GuideIds.FAT, GuideIds.SUGAR),
            personalNote = { p -> p.planTargetsIfAny()?.let { "Your plan's carbohydrate target is ${it.carbsG} g a day: the calories left after protein and fat." } },
        ),

        Guide(
            id = GuideIds.FAT,
            title = "Fat",
            summary = "Essential, energy-dense and easy to under-count.",
            sections = listOf(
                GuideSection(
                    paragraphs = listOf(
                        "Dietary fat provides essential fatty acids your body can't make, helps you absorb vitamins A, D, E and K, and is needed to make some hormones. At 9 kcal per gram it's the most energy-dense nutrient.",
                    ),
                ),
                GuideSection(
                    heading = "Types of fat",
                    bullets = listOf(
                        "Unsaturated fats, in olive and rapeseed oil, nuts, seeds, avocado and oily fish (which also provides omega-3), are the ones to favour.",
                        "Saturated fat, mostly in fatty meat, butter, cheese and coconut or palm oil: the WHO advises keeping it under 10% of energy.",
                        "Trans fats, in some fried and processed foods: the WHO advises under 1% of energy.",
                    ),
                ),
                GuideSection(
                    heading = "Trade-offs",
                    bullets = listOf(
                        "Fat adds flavour and helps meals feel satisfying.",
                        "Because it's so energy-dense, small amounts add up fast. Cooking oil and dressings are the most common things people forget to log, and they're hard to see in a photo.",
                    ),
                ),
                GuideSection(
                    paragraphs = listOf(
                        "FitCal's plan sets fat at about 25% of calories and never below 0.6 g per kg, so very low-calorie targets don't push fat too low.",
                    ),
                ),
            ),
            sources = listOf(
                "WHO (2023), Saturated fatty acid and trans-fatty acid intake for adults and children: guideline",
                "EFSA (2010), Dietary reference values for fats",
            ),
            related = listOf(GuideIds.PROTEIN, GuideIds.CARBS, GuideIds.PHOTO_ESTIMATES),
            personalNote = { p -> p.planTargetsIfAny()?.let { "Your plan's fat target is ${it.fatG} g a day." } },
        ),

        Guide(
            id = GuideIds.PHOTO_ESTIMATES,
            title = "Why photo estimates vary",
            summary = "What a photo can and can't tell FitCal, and how to make estimates better.",
            sections = listOf(
                GuideSection(
                    paragraphs = listOf(
                        "FitCal recognises the foods in your photo and estimates each portion's weight, then works out nutrition from that. Recognising foods is usually reliable; judging how much is the hard part.",
                    ),
                ),
                GuideSection(
                    heading = "What makes estimates less accurate",
                    bullets = listOf(
                        "Depth: a photo is flat, so a deep bowl and a shallow plate can look alike.",
                        "Hidden ingredients: oil, butter, sauces and sugar mixed into food are hard or impossible to see.",
                        "Mixed dishes such as stews, curries and casseroles, where ingredients overlap.",
                        "Density: the same volume of rice or pasta can weigh quite different amounts.",
                    ),
                ),
                GuideSection(
                    heading = "How to get better numbers",
                    bullets = listOf(
                        "Set your usual plate size in Settings so FitCal can judge scale.",
                        "Shoot from above, in good light, with the whole plate in view.",
                        "Check the weights on the review screen and correct anything that looks off. You can also reopen a logged meal from the dashboard.",
                        "Add cooking oil or sauces as separate items.",
                        "Now and then, weigh a typical meal on a kitchen scale to calibrate your eye.",
                    ),
                ),
                GuideSection(
                    paragraphs = listOf(
                        "Small errors tend to even out over a week. Consistent logging matters more than perfect numbers for any single meal.",
                    ),
                ),
            ),
            sources = listOf(
                "FitCal, how meal analysis works (in-app review screen)",
            ),
            related = listOf(GuideIds.CALORIES, GuideIds.FAT),
        ),

        Guide(
            id = GuideIds.PROFESSIONAL_HELP,
            title = "When to talk to a professional",
            summary = "Situations where general guidance isn't enough.",
            sections = listOf(
                GuideSection(
                    paragraphs = listOf(
                        "FitCal gives general estimates. Speak to a doctor or registered dietitian before changing how you eat if any of these apply:",
                    ),
                    bullets = listOf(
                        "You have a medical condition such as diabetes, kidney disease or heart disease, or take regular medication.",
                        "You are pregnant, trying to become pregnant or breastfeeding.",
                        "You are recovering from illness or surgery.",
                    ),
                ),
                GuideSection(
                    heading = "Signs a plan may be too hard on you",
                    bullets = listOf(
                        "Losing more than about 1 kg a week for several weeks.",
                        "Feeling dizzy, very tired, cold all the time, or unable to concentrate.",
                        "Changes to your menstrual cycle.",
                    ),
                ),
                GuideSection(
                    heading = "Your relationship with food",
                    paragraphs = listOf(
                        "Tracking isn't right for everyone. If you notice yourself feeling guilty or anxious about eating, skipping meals to stay under a number, compensating with exercise, or thinking about food or weight most of the day, please stop tracking and reach out to a doctor or your national eating-disorder or mental-health helpline. You deserve support, and it helps.",
                    ),
                ),
            ),
            sources = listOf(
                "NHS, Eating disorders: overview",
                "NHS, Losing weight: getting started",
            ),
            related = listOf(GuideIds.TRACKING_WEIGHT),
            adFree = true,
        ),
    )

    fun byId(id: String): Guide? = all.firstOrNull { it.id == id }

    /** Dashboard macro cards link to these. */
    fun forMacro(title: String): String? = when (title.lowercase()) {
        "protein" -> GuideIds.PROTEIN
        "carbs" -> GuideIds.CARBS
        "fats", "fat" -> GuideIds.FAT
        else -> null
    }
}
