package io.github.rajvinodh.orbit.gradle

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class GradleOverridesTest {

  @Test
  fun `parseList trims and drops empty entries`() {
    assertThat(GradleOverrides.parseList(" a, b ,,c ")).containsExactly("a", "b", "c")
  }

  @Test
  fun `parseArgs splits on first equals only`() {
    assertThat(GradleOverrides.parseArgs("mock=true, url=https://x.io/?a=b"))
      .containsEntry("mock", "true")
      .containsEntry("url", "https://x.io/?a=b")
  }

  @Test
  fun `parseArgs rejects entries without a key`() {
    assertThatThrownBy { GradleOverrides.parseArgs("novalue") }
      .isInstanceOf(IllegalArgumentException::class.java)
      .hasMessageContaining("expected key=value")
  }
}
