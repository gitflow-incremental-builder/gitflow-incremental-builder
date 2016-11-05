# gitflow-incremental-builder

A maven extension for incremental building of multi-module projects when using [feature branches (Git Flow)](http://nvie.com/posts/a-successful-git-branching-model/).
Builds or tests only changed maven modules compared to reference branch in Git (e.g. origin/develop) and all their dependents.


## Usage

- Add into maven pom file:
```xml
<build>
    <extensions>
        <extension>
            <groupId>com.vackosar.gitflowincrementalbuilder</groupId>
            <artifactId>gitflow-incremental-builder</artifactId>
            <version>2.5</version>
        </extension>
    </extensions>
    ...
</build>
```
## Configuration

Maven pom properties configuration with default values is below:
```xml
<properties>
	<gib.enabled>true</gib.enabled>
	<gib.repositorySshKey></gib.repositorySshKey>
	<gib.referenceBranch>refs/remotes/origin/develop</gib.referenceBranch>
	<gib.baseBranch>HEAD</gib.baseBranch>
	<gib.uncommited>true</gib.uncommited>
	<gib.skipTestsForNotImpactedModules>false</gib.skipTestsForNotImpactedModules>
	<gib.buildAll>false</gib.buildAll>
	<gib.compareToMergeBase>true</gib.compareToMergeBase>
	<gib.fetchBaseBranch>false</gib.fetchBaseBranch>
	<gib.fetchReferenceBranch>false</gib.fetchReferenceBranch>
</properties>
```

## Requirements

- Maven version 3+.
