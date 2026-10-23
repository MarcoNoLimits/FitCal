package com.fitcal.app.review

import com.fitcal.app.ui.screens.review.components.HIDDEN_INGREDIENTS
import com.fitcal.app.ui.screens.review.components.caloriesAtCurrentWeight
import com.fitcal.app.ui.screens.review.components.hiddenIngredientsToOffer
import com.fitcal.app.ui.screens.review.components.toEditable
import kotlin.test.Test
import kotlin.test.assertEquals

class HiddenIngredientsTest {

    @Test
    fun offersOnlyWhatTheScanDidNotFind() {
        val offered = hiddenIngredientsToOffer(listOf("Grilled salmon", "Olive oil drizzle", "Rice"))
        assertEquals(listOf("butter", "dressing"), offered.map { it.key })
        assertEquals(3, hiddenIngredientsToOffer(listOf("Chicken breast")).size)
        assertEquals(listOf("oil", "butter"), hiddenIngredientsToOffer(listOf("Caesar salad with dressing")).map { it.key })
    }

    @Test
    fun typicalAmountsMatchUsdaValues() {
        val kcal = HIDDEN_INGREDIENTS.associate { it.key to it.toEditable().caloriesAtCurrentWeight() }
        assertEquals(124, kcal["oil"])      // 1 tbsp vegetable oil
        assertEquals(100, kcal["butter"])   // 1 tbsp butter
        assertEquals(102, kcal["dressing"]) // 2 tbsp, middle estimate
    }
}
