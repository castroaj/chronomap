package io.github.castroaj.chronomap;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * A {@link VersionSource} over versions held in memory. The checksum is the SHA-256 of each row's
 * {@code toString}, so rows need a value-based {@code toString} (records have one).
 *
 * @param <RECORD> the row type of the table type
 */
public final class InMemorySource<RECORD> implements VersionSource<RECORD> {
    private final Map<VersionRef, List<RECORD>> versions = new LinkedHashMap<>();

    /**
     * Creates a source over the given versions, which may be passed in any order.
     *
     * @param versions the versions to hold
     * @param <RECORD> the row type of the table type
     * @return a source listing the versions oldest first
     */
    @SafeVarargs
    public static <RECORD> InMemorySource<RECORD> of(Version<RECORD>... versions) {
        var versionsList = new ArrayList<Version<RECORD>>();
        for (var version : versions) {
            versionsList.add(version);
        }
        return new InMemorySource<>(versionsList);
    }

    private InMemorySource(List<Version<RECORD>> input) {
        input.stream()
                .sorted(Comparator.comparing(Version::date))
                .forEach(v -> versions.put(refFor(v), v.rows()));
    }

    /** {@inheritDoc} */
    @Override
    public List<VersionRef> list(LocalDate after) {
        return versions.keySet().stream()
                .filter(ref -> after == null || ref.date().isAfter(after))
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException if {@code ref} is not a version of this source
     */
    @Override
    public Stream<RECORD> read(VersionRef ref) {
        List<RECORD> rows = versions.get(ref);
        if (rows == null) {
            throw new IllegalArgumentException("Unknown version: " + ref.id());
        }
        return rows.stream();
    }

    private static <RECORD> VersionRef refFor(Version<RECORD> version) {
        return new VersionRef(version.id(), version.date(), checksum(version.rows()));
    }

    private static String checksum(List<?> rows) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Object row : rows) {
                byte[] bytes = String.valueOf(row).getBytes(StandardCharsets.UTF_8);
                digest.update(Integer.toString(bytes.length).getBytes(StandardCharsets.UTF_8));
                digest.update((byte) ':');
                digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required on every Java platform", e);
        }
    }
}
