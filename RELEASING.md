# How to release

A brief description of how to release/deploy GIB.

Note: Releases and SNAPSHOTs are deployed via [`nexus-staging-maven-plugin`](https://github.com/sonatype/nexus-maven-plugins/tree/master/staging/maven-plugin).

## Prerequisites

- You must be a **collaborator** of GIB (or the owner)
- **GPG**
  - https://www.gnupg.org/download/
  - Key is published
  - `settings.xml` contains something like:
    ```xml
	<profiles>
      <profile>
        <id>ossrh</id>
        <properties>
          <gpg.executable>C:\Program Files (x86)\GnuPG\bin\gpg.exe</gpg.executable>
  	      <gpg.keyname>your-key-fingerprint</gpg.keyname>
        </properties>
      </profile>
    </profiles>
    ```
	(Windows example)
- **OSSRH** access
  - https://central.sonatype.org/pages/ossrh-guide.html
  - permissions have been granted (may require a ticket by the GIB owner or another collaborator)
  - `settings.xml` contains something like:
    ```xml
	<servers>
      <server>
        <id>ossrh</id>
        <username>your-OSSRH-Jira-username</username>
        <password>your-OSSRH-Jira-password</password>
      </server>
    </servers>
    ```

## Perform a release

- :information_source: `project/scm/developerConnection` in `pom.xml` is set to `https` protocol (_not_ `ssh` or `git`)
- `mvn -Prelease,ossrh release:prepare`
- `mvn -Prelease,ossrh release:perform`
- see also: https://maven.apache.org/maven-release/maven-release-plugin/

## Deploy a SNAPSHOT

- `mvn -Prelease,ossrh clean deploy`
- note: `pom.xml` is left untouched
