# How to release

A brief description of how to release/deploy GIB.

Note: Releases and SNAPSHOTs are deployed via [`central-publishing-maven-plugin`](https://central.sonatype.org/publish/publish-portal-maven/).

## Prerequisites

- You must be a **collaborator** of GIB (or the owner)

- **GPG**
  - [GnuPG download](https://www.gnupg.org/download/])

  - Key is published

  - `settings.xml` contains something like:
      ```xml
      <profiles>
        <profile>
          <id>publish</id>
          <properties>
            <gpg.executable>C:\Program Files (x86)\GnuPG\bin\gpg.exe</gpg.executable>
            <gpg.keyname>your-key-fingerprint</gpg.keyname>
          </properties>
        </profile>
      </profiles>
      ```

- **Maven Central** access
  - [Central Portal guide](https://central.sonatype.org/register/central-portal/)

  - permissions have been granted (may require a ticket by the GIB owner or another collaborator)
  
  - `settings.xml` contains something like:
      ```xml
      <servers>
        <server>
          <id>central</id>
          <username>your-central-token-username</username>
          <password>your-central-token-password</password>
        </server>
      </servers>
      ```
      Note: Log into https://central.sonatype.org and proceed as described [here](https://central.sonatype.org/publish/generate-portal-token/) to get the token info.

## Perform a release

- :information_source: `project/scm/developerConnection` in `pom.xml` is set to `https` protocol (_not_ `ssh` or `git`)
- `mvn -Ppublish release:prepare`
- `mvn -Ppublish release:perform`
- see also [`maven-release-plugin`](https://maven.apache.org/maven-release/maven-release-plugin/)

## Deploy a SNAPSHOT

- `mvn -Ppublish clean deploy`
- note: `pom.xml` is left untouched
