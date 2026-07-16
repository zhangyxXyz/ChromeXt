package org.matrix.chromext.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebDavConfigTest {
  @Test
  fun operationsStayUnavailableUntilRequiredFieldsAreComplete() {
    assertFalse(WebDavConfig("", "user", "password").isConfigured)
    assertFalse(WebDavConfig("https://dav.example.com", "", "password").isConfigured)
    assertFalse(WebDavConfig("https://dav.example.com", "user", "").isConfigured)
    assertTrue(WebDavConfig("https://dav.example.com", "user", "password").isConfigured)
  }
}
