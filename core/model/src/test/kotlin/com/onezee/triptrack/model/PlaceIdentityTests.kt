// iOS: PlaceIdentityTests.swift (UUID contract; geohash and pass predicates belong to core:logic)
package com.onezee.triptrack.model

import java.util.UUID
import kotlin.test.*

class PlaceIdentityTests {
    @Test fun idIsDeterministicUUIDv5() {
        assertEquals(UUID.fromString("2D124877-5BCA-5830-B12E-A09F8176B7D9"), Place.id(forCell = "u4pruyd"))
        assertEquals(UUID.fromString("5900AC91-1D76-5169-9437-EA51B6D37507"), Place.id(forCell = "v0b3xg2"))
        assertEquals(Place.id("u4pruyd"), Place.id("u4pruyd"))
        assertNotEquals(Place.id("u4pruyd"), Place.id("u4pruye"))
    }

    @Test fun idCarriesVersionAndVariantBits() {
        assertEquals(5, Place.id("u4pruyd").version())
        assertEquals(2, Place.id("u4pruyd").variant())
    }
}
