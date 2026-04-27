package io.github.theodoremeyer.simplevoicegeyser.core.update;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A simple implementation of semantic versioning parsing and comparison.
 * Only supports major.minor.patch and an optional prerelease tag.
 */
public final class SemanticVersion implements Comparable<SemanticVersion> {

    /**
     * Semantic Pattern
     */
    private static final Pattern SEMVER =
            Pattern.compile("^(\\d+)\\.(\\d+)\\.(\\d+)(?:-([A-Za-z0-9.-]+))?$");

    /**
     * Major version
     */
    public final int major;

    /**
     * Minor Version
     */
    public final int minor;

    /**
     * Patch Version
     */
    public final int patch;

    /**
     * Additional tag (e.g. "alpha", "beta", "rc1").
     */
    public final String tag;

    /**
     * Create a verison
     * @param major major
     * @param minor minor
     * @param patch patch
     * @param tag added tags
     */
    private SemanticVersion(int major, int minor, int patch, String tag) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.tag = tag;
    }

    /**
     * Parse a string into Semantic
     * @param input input string
     * @return Semantic Version
     */
    public static SemanticVersion parse(String input) {
        Matcher m = SEMVER.matcher(input);

        if (!m.matches()) {
            // safest fallback: treat invalid versions as very old
            return new SemanticVersion(0, 0, 0, null);
        }

        return new SemanticVersion(
                Integer.parseInt(m.group(1)),
                Integer.parseInt(m.group(2)),
                Integer.parseInt(m.group(3)),
                m.group(4)
        );
    }

    @Override
    public int compareTo(SemanticVersion o) {
        int c = Integer.compare(major, o.major);
        if (c != 0) return c;

        c = Integer.compare(minor, o.minor);
        if (c != 0) return c;

        c = Integer.compare(patch, o.patch);
        if (c != 0) return c;

        // stable > prerelease
        if (tag == null && o.tag != null) return 1;
        if (tag != null && o.tag == null) return -1;
        if (tag == null) return 0;

        return tag.compareToIgnoreCase(o.tag);
    }

    /**
     * See if one tag is newer than another
     * @param other tag to check
     * @return if it is newer
     */
    public boolean isNewerThan(SemanticVersion other) {
        return compareTo(other) > 0;
    }
}