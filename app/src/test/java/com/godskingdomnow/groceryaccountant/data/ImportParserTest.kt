package com.godskingdomnow.groceryaccountant.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ImportParserTest {
    private fun names(t: String) = ImportParser.parse(t).filter { it.likely }.map { it.name }

    @Test fun casualSentence() {
        val r = ImportParser.parse("Can you grab milk, 2 dozen eggs and bread? Thanks! Love you ❤️")
        assertEquals(listOf("Milk", "Eggs", "Bread"), r.map { it.name })
        assertEquals(2, r[1].qty); assertEquals("1 dozen", r[1].size)
    }

    @Test fun oneItemPerLine() {
        assertEquals(listOf("Taco shells", "Pico", "Sour cream", "The holiday coke sodas"),
            names("Taco shells\nPico\nSour cream\nThe holiday coke sodas"))
    }

    @Test fun bulletsQuantitiesSizes() {
        val r = ImportParser.parse("- 2 lb ground beef\n- 3 cans of black beans\n- bananas x2\n• mac and cheese (3)\n☐ paper towels\n1. Diapers size 4")
        assertEquals(listOf("Ground beef", "Black beans", "Bananas", "Mac and cheese", "Paper towels", "Diapers size 4"), r.map { it.name })
        assertEquals("2 lb", r[0].size); assertEquals(1, r[0].qty)
        assertEquals(3, r[1].qty); assertEquals("can", r[1].size)
        assertEquals(2, r[2].qty); assertEquals(3, r[3].qty)
    }

    @Test fun storeAppStyleWithPrices() {
        val r = ImportParser.parse("Walmart:\nGreat Value Whole Milk, 1 Gallon\n$3.48\nBananas $1.29")
        assertEquals("Great Value Whole Milk", r[0].name); assertEquals("1 gallon", r[0].size); assertEquals(348L, r[0].priceCents)
        assertEquals(129L, r[1].priceCents)
    }

    @Test fun chatterAndDuplicates() {
        val r = ImportParser.parse("We're out of coffee.\nDid you see my message about Sunday?\nok\nmilk\nMilk")
        assertEquals("Coffee", r[0].name)
        assertFalse(r[1].likely)
        assertEquals(2, r.first { it.name == "Milk" }.qty)
        assertEquals(3, r.size)
    }
}
