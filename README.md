# gitflow-incremental-builder

A maven extension for incremental building of multi-module projects when using Git Flow.
- Builds only changed maven modules compared to reference branch in Git (e.g. origin/develop) and all their dependents.
- Useful for multi-module maven projects using Gitflow model, where "origin/develop" is always stable.
- Extension is configured using maven POM properties or JVM properties.
- Don't forget to git-fetch first.

## Usage

- Add into build:
```xml
<build>
    <extensions>
        <extension>
            <groupId>com.vackosar.gitflowincrementalbuilder</groupId>
            <artifactId>gitflow-incremental-builder</artifactId>
            <version>1.4</version>
        </extension>
    </extensions>
    ...
</build>
```
## Configuration

Properties with default values are below:
```xml
<properties>
	<gib.enabled>true</gib.enabled>
	<gib.repositorySshKey></gib.repositorySshKey>
	<gib.referenceBranch>refs/remotes/origin/develop</gib.referenceBranch>
	<gib.baseBranch>HEAD</gib.baseBranch>
	<gib.uncommited>true</gib.uncommited>
	<gib.skipTestsForNotImpactedModules>false</gib.skipTestsForNotImpactedModules>
	<gib.buildAll>false</gib.buildAll>
</properties>
```
