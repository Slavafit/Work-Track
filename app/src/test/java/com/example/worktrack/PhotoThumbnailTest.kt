package com.example.worktrack

import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoThumbnailTest {
    @Test fun `large images are sampled close to thumbnail size`() {
        assertEquals(8, thumbnailSampleSize(4000, 3000, 256))
        assertEquals(32, thumbnailSampleSize(12000, 500, 256))
    }

    @Test fun `small and invalid image sizes are not sampled`() {
        assertEquals(1, thumbnailSampleSize(255, 200, 256))
        assertEquals(1, thumbnailSampleSize(0, 200, 256))
        assertEquals(1, thumbnailSampleSize(4000, 3000, 0))
    }
}
