package com.fitcal.app

import com.fitcal.app.learn.GuideIds
import com.fitcal.app.learn.Guides
import com.fitcal.app.plan.BodyStats
import com.fitcal.app.plan.PlanGoal
import com.fitcal.app.plan.PlanPace
import com.fitcal.app.plan.startPlan
import com.fitcal.app.ui.components.ActivityLevel
import com.fitcal.shared.model.UserProfile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Library → Guides: content integrity, editorial rules, and the "For you" notes. */
class GuidesTest {

    private val noPlan = UserProfile(weight = 70f, height = 175f, calGoal = 2000, proteinGoal = 150, carbsGoal = 200, fatGoal = 65)
    private val withPlan = noPlan.startPlan(
        BodyStats(80f, 180f, 30, "Male", ActivityLevel.MODERATE), PlanGoal.LOSE, PlanPace.STEADY, 72f, "2026-10-13",
    )

    private fun text(id: String) = Guides.byId(id)!!.sections
        .flatMap { listOfNotNull(it.heading) + it.paragraphs + it.bullets }.joinToString(" ")

    @Test
    fun idsAreUniqueAndEveryLinkResolves() {
        val ids = Guides.all.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        Guides.all.forEach { g -> g.related.forEach { assertNotNull(Guides.byId(it), "${g.id} links to missing $it") } }
        listOf("Protein", "Carbs", "Fats").forEach { assertNotNull(Guides.byId(Guides.forMacro(it)!!)) }
    }

    @Test
    fun everyGuideHasContentSourcesAndAReadingTime() {
        assertEquals(8, Guides.all.size)
        Guides.all.forEach { g ->
            assertTrue(g.sections.isNotEmpty(), g.id)
            assertTrue(g.sources.isNotEmpty(), "${g.id} has no sources")
            assertTrue(g.readMinutes in 1..5, "${g.id}: ${g.readMinutes} min")
        }
    }

    @Test
    fun noMoralisingOrDietCultureLanguage() {
        // Facts and trade-offs only; this is an 18+ app with eating-disorder safeguards.
        val banned = listOf("junk food", "cheat meal", "clean eating", "guilt-free", "burn it off", "bad food", "good food", "sinful")
        Guides.all.forEach { g ->
            val body = (text(g.id) + " " + g.title + " " + g.summary).lowercase()
            banned.forEach { assertTrue(it !in body, "${g.id} uses \"$it\"") }
        }
    }

    @Test
    fun keyFactsAreStatedConsistently() {
        assertTrue("1 kcal = 4.184 kJ" in text(GuideIds.CALORIES))
        assertTrue("Fat: 9 kcal per gram" in text(GuideIds.CALORIES))
        assertTrue("7,700" in text(GuideIds.ENERGY_BALANCE))
        assertTrue("0.8 g per kg" in text(GuideIds.PROTEIN))
    }

    @Test
    fun personalNotesUseThePlansNumbers() {
        // Male 80 kg, moderate, steady loss: maintenance 2,759, target 2,210 (see PersonalPlanTest).
        val energy = Guides.byId(GuideIds.ENERGY_BALANCE)!!.personalNote(withPlan)!!
        assertTrue("2,759 kcal" in energy, energy)
        assertTrue("2,210 kcal" in energy, energy)
        assertTrue("549 kcal daily deficit" in energy, energy)
        assertTrue("0.5 kg a week" in energy, energy)

        val calories = Guides.byId(GuideIds.CALORIES)!!.personalNote(withPlan)!!
        assertTrue("9,247 kJ" in calories, calories)   // 2,210 × 4.184

        val protein = Guides.byId(GuideIds.PROTEIN)!!.personalNote(withPlan)!!
        assertTrue("96 to 160 g" in protein, protein)   // 80 kg × 1.2–2.0
        assertTrue("160 g" in protein, protein)
    }

    @Test
    fun noPersonalNotesWithoutAPlan() {
        Guides.all.forEach { assertNull(it.personalNote(noPlan), it.id) }
    }
}
