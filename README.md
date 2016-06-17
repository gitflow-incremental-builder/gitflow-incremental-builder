# gitflow-incremental-builder

A maven extension for incremental building of multi-module projects when using [feature branches (Git Flow)](http://nvie.com/posts/a-successful-git-branching-model/).
Builds or tests only changed maven modules compared to reference branch in Git (e.g. origin/develop) and all their dependents.


## Usage

- Add into build:
```xml
<build>
    <extensions>
        <extension>
            <groupId>com.vackosar.gitflowincrementalbuilder</groupId>
            <artifactId>gitflow-incremental-builder</artifactId>
            <version>1.7</version>
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

## Requirements

- Maven version 3+.
