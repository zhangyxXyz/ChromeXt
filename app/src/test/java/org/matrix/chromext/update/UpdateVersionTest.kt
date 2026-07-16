package org.matrix.chromext.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateVersionTest {
  @Test
  fun acceptsCommonReleaseTagFormats() {
    assertTrue(isSameVersion("v3.8.10", "3.8.10"))
    assertTrue(isSameVersion("3.8.10.0", "3.8.10"))
    assertTrue(isNewerVersion("v3.9.0", "3.8.10"))
    assertTrue(isNewerVersion("3.8.11-beta.1", "3.8.10"))
  }

  @Test
  fun olderOrSameVersionsDoNotPrompt() {
    assertFalse(isNewerVersion("3.8.10", "3.8.10"))
    assertFalse(isNewerVersion("v3.7.99", "3.8.10"))
  }
}
