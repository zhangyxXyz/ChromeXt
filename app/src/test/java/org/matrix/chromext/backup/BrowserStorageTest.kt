package org.matrix.chromext.backup

import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserStorageTest {
  @Test
  fun browserPackageBecomesSafeDirectoryName() {
    assertEquals("com.android.chrome", browserDirectoryName(" com.android.chrome "))
    assertEquals("browser-name", browserDirectoryName("browser/name"))
    assertEquals("browser", browserDirectoryName("///"))
  }

  @Test
  fun webDavBrowserDirectoryIsNestedUnderConfiguredRoot() {
    val config = WebDavConfig("https://dav.example.com", "user", "password", "ChromeXt/")

    assertEquals("ChromeXt/com.android.chrome", config.forBrowser("com.android.chrome").directory)
  }
}
