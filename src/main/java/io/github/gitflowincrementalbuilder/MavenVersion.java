package io.github.gitflowincrementalbuilder;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.rtinfo.RuntimeInformation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Named
class MavenVersion {

    private Logger logger = LoggerFactory.getLogger(MavenVersion.class);

    // note: "[4,)" would NOT match pre-releases like 4.0.0-rc-7 (qualifiers sort before the release), hence "4-alpha"
    private static final String MAVEN_4_OR_LATER = "[4-alpha,)";

    // tested/known-good Maven 3 line (3.9.6 up to but excluding 3.11.x)
    private static final String SUPPORTED_MAVEN_3_MINOR_RANGE = "[3.9.6,3.11-alpha)";

    // note: use "4.1-alpha" as exclusive upper bound so that 4.0 pre-releases are included but 4.1+ is excluded
    private static final String SUPPORTED_MAVEN_4_MINOR_RANGE = "[4.0.0-rc-7,4.1-alpha)";

    @Inject private RuntimeInformation runtimeInformation;

    boolean isMaven4OrHigher() {
        return getVersionString() != null && runtimeInformation.isMavenVersion(MAVEN_4_OR_LATER);
    }

    String getVersionString() {
        String mavenVersion = runtimeInformation.getMavenVersion();
        if (mavenVersion == null || mavenVersion.isBlank()) {
            logger.warn("Could not determine Maven version.");
            return null;
        }
        return mavenVersion;
    }

    boolean isSupported() {
        String mavenVersion = getVersionString();
        return mavenVersion != null
                && (runtimeInformation.isMavenVersion(SUPPORTED_MAVEN_3_MINOR_RANGE)
                || runtimeInformation.isMavenVersion(SUPPORTED_MAVEN_4_MINOR_RANGE));
    }
}
