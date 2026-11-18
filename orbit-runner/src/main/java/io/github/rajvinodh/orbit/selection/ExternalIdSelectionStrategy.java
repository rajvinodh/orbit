package io.github.rajvinodh.orbit.selection;

import io.github.rajvinodh.orbit.domain.DiscoveryResult;
import io.github.rajvinodh.orbit.domain.TestCase;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class ExternalIdSelectionStrategy implements TestSelectionStrategy {

  private final List<String> externalIds;

  private ExternalIdSelectionStrategy(List<String> externalIds) {
    this.externalIds = externalIds.stream().distinct().collect(Collectors.toList());
  }

  /** Accepts comma-separated IDs or @/path/to/file (one ID per line). */
  public static ExternalIdSelectionStrategy from(String input) {
    if (input.startsWith("@")) {
      File file = new File(input.substring(1));
      try {
        List<String> ids =
            Files.readAllLines(file.toPath()).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
        return new ExternalIdSelectionStrategy(ids);
      } catch (IOException e) {
        throw new IllegalArgumentException("Cannot read external IDs file: " + file, e);
      }
    }
    List<String> ids =
        Arrays.stream(input.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList());
    return new ExternalIdSelectionStrategy(ids);
  }

  @Override
  public List<TestCase> select(DiscoveryResult discovery) {
    return externalIds.stream()
        .map(id -> discovery.getByExternalId().get(id))
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }
}
