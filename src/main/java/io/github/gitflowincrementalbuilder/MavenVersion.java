package io.github.gitflowincrementalbuilder;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.apache.maven.rtinfo.RuntimeInformation;

@Singleton
@Named
class MavenVersion {

    // note: "[4,)" would NOT match pre-releases like 4.0.0-rc-7 (qualifiers sort before the release), hence "4-alpha"
    static final String MAVEN_4_OR_LATER = "[4-alpha,)";

    @Inject private RuntimeInformation runtimeInformation;

    boolean isMaven4OrHigher() {
        return runtimeInformation.isMavenVersion(MAVEN_4_OR_LATER);
    }

    String getVersionString() {
        return runtimeInformation.getMavenVersion();
    }

    boolean isSupported() {
        String mavenVersion = getVersionString();
        return mavenVersion != null
                && (mavenVersion.startsWith("3.9.")
                || mavenVersion.startsWith("3.10.")
                || mavenVersion.startsWith("4.0.")
                || mavenVersion.startsWith("3.8.")
                || mavenVersion.equals("3.6.3"));
    }
}
