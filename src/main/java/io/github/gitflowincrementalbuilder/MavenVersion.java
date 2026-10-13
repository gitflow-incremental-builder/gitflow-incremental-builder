package io.github.gitflowincrementalbuilder;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.rtinfo.RuntimeInformation;

@Singleton
@Named
class MavenVersion {

    // note: "[4,)" would NOT match pre-releases like 4.0.0-rc-7 (qualifiers sort before the release), hence "4-alpha"
    private static final String MAVEN_4_OR_LATER = "[4-alpha,)";

    // note: use "4.1-alpha" as exclusive upper bound so that 4.0 pre-releases are included but 4.1+ is excluded
    private static final String SUPPORTED_MAVEN_VERSIONS = "[3.6.3,4.1-alpha)";

    @Inject private RuntimeInformation runtimeInformation;

    boolean isMaven4OrHigher() {
        return runtimeInformation.isMavenVersion(MAVEN_4_OR_LATER);
    }

    String getVersionString() {
        return runtimeInformation.getMavenVersion();
    }

    boolean isSupported() {
        return getVersionString() != null && runtimeInformation.isMavenVersion(SUPPORTED_MAVEN_VERSIONS);
    }
}
