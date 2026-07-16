package org.matrix.chromext.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScriptEditorFormatterTest {
  @Test
  fun routesLargeOrVeryLongScriptsToTheBoundedPlatformEditor() {
    assertFalse(shouldUsePlatformEditor(MAX_COMPOSE_EDITOR_SOURCE_LENGTH, MAX_COMPOSE_EDITOR_LINES))
    assertTrue(shouldUsePlatformEditor(MAX_COMPOSE_EDITOR_SOURCE_LENGTH + 1, 10))
    assertTrue(shouldUsePlatformEditor(1_000, MAX_COMPOSE_EDITOR_LINES + 1))
  }

  @Test
  fun parsesAndReplacesEditableMetadataWithoutTouchingOtherDirectives() {
    val meta =
        """// ==UserScript==
        |// @name Demo
        |// @match https://old.example/*
        |// @include *://included.example/*
        |// @exclude https://old.example/private/*
        |// @grant GM.getValue
        |// ==/UserScript==
        |"""
            .trimMargin()
    val parsed = parseMetadataDraft(meta)
    assertEquals(listOf("https://old.example/*"), parsed.matchRules)
    assertEquals(listOf("*://included.example/*"), parsed.includeRules)
    assertEquals(listOf("GM.getValue"), parsed.grants)

    val updated =
        replaceMetadataRules(
            meta,
            parsed.copy(
                matches = "https://new.example/*\nhttps://second.example/*",
                excludes = "https://new.example/private/*",
            ),
        )
    assertTrue(updated.contains("// @name Demo"))
    assertTrue(updated.contains("// @grant GM.getValue"))
    assertTrue(updated.contains("// @match https://new.example/*"))
    assertTrue(updated.contains("// @match https://second.example/*"))
    assertFalse(updated.contains("old.example/private"))
  }

  @Test
  fun replacesOnlyTheMetadataBlockInFullSource() {
    val source =
        """// ==UserScript==
        |// @name Demo
        |// @match https://example.com/*
        |// ==/UserScript==
        |
        |console.log('body');
        |"""
            .trimMargin()
    val nextMeta = extractMetadataBlock(source).replace("Demo", "Updated")
    val result = replaceMetadataBlock(source, nextMeta)
    assertTrue(result.contains("// @name Updated"))
    assertTrue(result.contains("console.log('body');"))
  }

  @Test
  fun formatsNestedBlocksWithTwoSpaceIndentation() {
    val source =
        """function run() {
        |if (true) {
        |console.log('ok');
        |}
        |}
        |"""
            .trimMargin()

    assertEquals(
        """function run() {
        |  if (true) {
        |    console.log('ok');
        |  }
        |}
        |"""
            .trimMargin(),
        formatJavaScript(source),
    )
  }

  @Test
  fun ignoresBracesInsideStringsAndComments() {
    val source =
        """if (ready) {
        |const text = "}";
        |// {
        |/* } */
        |done();
        |}
        |"""
            .trimMargin()

    assertEquals(
        """if (ready) {
        |  const text = "}";
        |  // {
        |  /* } */
        |  done();
        |}
        |"""
            .trimMargin(),
        formatJavaScript(source),
    )
  }
}
