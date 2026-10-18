package com.fieldscanner.app.display

import org.junit.Assert.assertArrayEquals
import org.junit.Test

class DisplayGeometryCoordinatesTest {
    @Test
    fun `left zone uses the approved enlarged lower outline`() {
        assertArrayEquals(
            floatArrayOf(185f, 641f, 185f, 691f, 150.5f, 712.5f, 74.5f, 705f, 44.5f, 691f, 35.5f, 618.5f, 61.5f, 561f),
            DisplayGeometryCoordinates.leftZone.sliceArray(8..21),
            0f
        )
    }

    @Test
    fun `other display zones retain their pre task nine coordinates`() {
        assertArrayEquals(floatArrayOf(260.8f, 39.2f, 164f, 128.1f, 157.6f, 144.6f, 189.7f, 505.1f, 194.8f, 516.8f, 259.1f, 587.8f, 279f, 593.7f, 661.7f, 491.8f, 665.9f, 491.2f, 986.4f, 475.6f, 1005.2f, 458.6f, 1050.7f, 152.5f, 1050.4f, 145.1f, 1031.4f, 60.3f, 1011.3f, 44.7f, 569.3f, 56.8f, 275.9f, 34f), DisplayGeometryCoordinates.centerZone, 0f)
        assertArrayEquals(floatArrayOf(924.3f, 619.2f, 924.3f, 533f, 902.1f, 513.1f, 639.6f, 541.1f, 637f, 541.6f, 332f, 618.8f, 317.1f, 641.3f, 325.6f, 694.4f, 345.3f, 711.3f, 931.5f, 711.3f, 949.9f, 683.4f, 925.9f, 627f), DisplayGeometryCoordinates.bottomZone, 0f)
        assertArrayEquals(floatArrayOf(1141.7f, 63.6f, 1090.8f, 124.3f, 1087.1f, 143.3f, 1111.8f, 220.1f, 1132.7f, 233.8f, 1222.1f, 225.7f, 1240f, 209.2f, 1255.3f, 120.5f, 1244.8f, 99.3f, 1166.2f, 58.7f), DisplayGeometryCoordinates.hintZones[0], 0f)
        assertArrayEquals(floatArrayOf(1203f, 296f, 1114.3f, 311.9f, 1097.9f, 331.8f, 1099.4f, 438.2f, 1124.7f, 457.2f, 1250.5f, 422.2f, 1263.4f, 394.8f, 1224.8f, 307.6f), DisplayGeometryCoordinates.hintZones[1], 0f)
        assertArrayEquals(floatArrayOf(1138.9f, 523.5f, 1055.8f, 526.2f, 1037.1f, 541.3f, 1020.7f, 606.2f, 1025.2f, 624.5f, 1080.8f, 686.2f, 1103.1f, 691.4f, 1167f, 665.5f, 1177.1f, 656.4f, 1206.2f, 602.4f, 1202.8f, 578.9f, 1153.7f, 529.4f), DisplayGeometryCoordinates.hintZones[2], 0f)
    }
}
