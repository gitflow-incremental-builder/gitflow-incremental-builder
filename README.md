# gitflow-incremental-builder
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.vackosar.gitflowincrementalbuilder/gitflow-incremental-builder/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.vackosar.gitflowincrementalbuilder/gitflow-incremental-builder)

![Travis CI](https://travis-ci.org/vackosar/gitflow-incremental-builder.svg?branch=master)

A maven extension for incremental building of multi-module projects when using [feature branches (Git Flow)](http://nvie.com/posts/a-successful-git-branching-model/).
Builds or tests only changed maven modules compared to reference branch in Git (e.g. origin/develop) and all their dependents.

## Table of Contents

- [Usage](#usage)
  * [Disable in IDE](#disable-in-ide)
- [Example](#example)
- [Configuration](#configuration)
  * [gib.enabled](#gibenabled)
  * [gib.disableBranchComparison](#gibdisablebranchcomparison)
  * [gib.uncommited](#gibuncommited)
  * [gib.untracked](#gibuntracked)
  * [gib.skipTestsForNotImpactedModules](#gibskiptestsfornotimpactedmodules)
  * [gib.argsForNotImpactedModules](#gibargsfornotimpactedmodules)
  * [gib.forceBuildModules](#gibforcebuildmodules)

## Usage

- Add into maven pom file:
```xml
<build>
    <extensions>
        <extension>
            <groupId>com.vackosar.gitflowincrementalbuilder</groupId>
            <artifactId>gitflow-incremental-builder</artifactId>
            <version>3.6</version>
        </extension>
    </extensions>
    ...
</build>
```

### Disable in IDE

As IDEs like IntelliJ IDEA or Eclipse usually apply their own custom strategy to building changed modules,
this extension should be disabled in such environments to avoid inconsistencies or errors (e.g. see issue #49).

See [gib.enabled](#gibenabled) in the configuration section.

## Example

Maven project `parent` has two submodules `child1` and `child2`:
```
$ ls *
pom.xml

child1:
pom.xml  src

child2:
pom.xml  src
```
`parent` has gitflow-incremental-builder configured:
```
$ cat pom.xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>parent</groupId>
    <artifactId>parent</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>pom</packaging>

    <properties>
        <gib.referenceBranch>refs/heads/develop</gib.referenceBranch>
    </properties>

    <modules>
        <module>child1</module>
        <module>child2</module>
    </modules>

    <build>
        <extensions>
            <extension>
                <groupId>com.vackosar.gitflowincrementalbuilder</groupId>
                <artifactId>gitflow-incremental-builder</artifactId>
                <version>3.6</version>
            </extension>
        </extensions>
    </build>

</project>
```
Currently checked out branch is `feature`. There is also a branch `develop`:
```
$ git branch
  develop
* feature
```
Branches are the same now:
```
$ git diff develop
```
Thus incremental does not build and test anything:
```
$ mvn test
[INFO] Scanning for projects...
[INFO] gitflow-incremental-builder starting...
[INFO] Git dir is: C:\Users\Vaclav\IdeaProjects\gitflow-incremental-builder\tmp\example\.git
[INFO] Head of branch HEAD is commit of id: commit 17b09f1f9ee9d2a56b7d7bf43d319db529cade9e 1483112738 -----p
[INFO] Head of branch refs/heads/develop is commit of id: commit 17b09f1f9ee9d2a56b7d7bf43d319db529cade9e 1483112738 -----p
[INFO] Using merge base of id: commit 17b09f1f9ee9d2a56b7d7bf43d319db529cade9e 1483112738 -tr-sp
[INFO] ------------------------------------------------------------------------
[INFO] Changed Artifacts:
[INFO]
[INFO]
[INFO] No changed artifacts to build. Executing validate goal only.
[INFO] gitflow-incremental-builder exiting...
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Build Order:
[INFO]
[INFO] child1
[INFO] child2
[INFO] parent
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] Building child1 1.0-SNAPSHOT
[INFO] ------------------------------------------------------------------------
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] Building child2 1.0-SNAPSHOT
[INFO] ------------------------------------------------------------------------
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] Building parent 1.0-SNAPSHOT
[INFO] ------------------------------------------------------------------------
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Summary:
[INFO]
[INFO] child1 ............................................. SUCCESS [  0.002 s]
[INFO] child2 ............................................. SUCCESS [  0.001 s]
[INFO] parent ............................................. SUCCESS [  0.000 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time: 0.953 s
[INFO] Finished at: 2016-12-30T16:56:35+01:00
[INFO] Final Memory: 9M/162M
[INFO] ------------------------------------------------------------------------
```
Now a file is written and added to git control making branches different:
```
$ echo "text" > child1/src/main/java/file1.txt
$ git add child1/src/main/java/file1.txt
$ git diff develop
diff --git a/child1/src/main/java/file1.txt b/child1/src/main/java/file1.txt
new file mode 100644
index 0000000..8e27be7
--- /dev/null
+++ b/child1/src/main/java/file1.txt
@@ -0,0 +1 @@
+text
warning: LF will be replaced by CRLF in child1/src/main/java/file1.txt.
The file will have its original line endings in your working directory.
```

Thus incremental build builds and tests only child1:
```
$ mvn test
[INFO] Scanning for projects...
[INFO] gitflow-incremental-builder starting...
[INFO] Git dir is: C:\Users\Vaclav\IdeaProjects\gitflow-incremental-builder\tmp\example\.git
[INFO] Head of branch HEAD is commit of id: commit 17b09f1f9ee9d2a56b7d7bf43d319db529cade9e 1483112738 -----p
[INFO] Head of branch refs/heads/develop is commit of id: commit 17b09f1f9ee9d2a56b7d7bf43d319db529cade9e 1483112738 -----p
[INFO] Using merge base of id: commit 17b09f1f9ee9d2a56b7d7bf43d319db529cade9e 1483112738 -tr-sp
[INFO] ------------------------------------------------------------------------
[INFO] Changed Artifacts:
[INFO]
[INFO] child1
[INFO]
[INFO] gitflow-incremental-builder exiting...
[INFO]
[INFO] ------------------------------------------------------------------------
[INFO] Building child1 1.0-SNAPSHOT
[INFO] ------------------------------------------------------------------------
[INFO]
[INFO] --- maven-resources-plugin:2.6:resources (default-resources) @ child1 ---
[WARNING] Using platform encoding (Cp1250 actually) to copy filtered resources, i.e. build is platform dependent!
[INFO] skip non existing resourceDirectory C:\Users\Vaclav\IdeaProjects\gitflow-incremental-builder\tmp\example\child1\src\main\resources
[INFO]
[INFO] --- maven-compiler-plugin:3.1:compile (default-compile) @ child1 ---
[INFO] Changes detected - recompiling the module!
[WARNING] File encoding has not been set, using platform encoding Cp1250, i.e. build is platform dependent!
[INFO] Compiling 1 source file to C:\Users\Vaclav\IdeaProjects\gitflow-incremental-builder\tmp\example\child1\target\classes
[INFO]
[INFO] --- maven-resources-plugin:2.6:testResources (default-testResources) @ child1 ---
[WARNING] Using platform encoding (Cp1250 actually) to copy filtered resources, i.e. build is platform dependent!
[INFO] skip non existing resourceDirectory C:\Users\Vaclav\IdeaProjects\gitflow-incremental-builder\tmp\example\child1\src\test\resources
[INFO]
[INFO] --- maven-compiler-plugin:3.1:testCompile (default-testCompile) @ child1 ---
[INFO] Changes detected - recompiling the module!
[WARNING] File encoding has not been set, using platform encoding Cp1250, i.e. build is platform dependent!
[INFO] Compiling 1 source file to C:\Users\Vaclav\IdeaProjects\gitflow-incremental-builder\tmp\example\child1\target\test-classes
[INFO]
[INFO] --- maven-surefire-plugin:2.12.4:test (default-test) @ child1 ---
[INFO] Surefire report directory: C:\Users\Vaclav\IdeaProjects\gitflow-incremental-builder\tmp\example\child1\target\surefire-reports

-------------------------------------------------------
 T E S T S
-------------------------------------------------------
Running ATest
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.041 sec

Results :

Tests run: 1, Failures: 0, Errors: 0, Skipped: 0

[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time: 2.043 s
[INFO] Finished at: 2016-12-30T17:01:56+01:00
[INFO] Final Memory: 18M/167M
[INFO] ------------------------------------------------------------------------
```

## Configuration

Maven pom properties configuration with default values is below:
```xml
<properties>
	<gib.enabled>true</gib.enabled>
	<gib.repositorySshKey></gib.repositorySshKey>
	<gib.disableBranchComparison>false</gib.disableBranchComparison>
	<gib.referenceBranch>refs/remotes/origin/develop</gib.referenceBranch>
	<gib.baseBranch>HEAD</gib.baseBranch>
	<gib.uncommited>true</gib.uncommited>
	<gib.untracked>true</gib.untracked>
	<gib.skipTestsForNotImpactedModules>false</gib.skipTestsForNotImpactedModules>
	<gib.argsForNotImpactedModules></gib.argsForNotImpactedModules>
	<gib.buildAll>false</gib.buildAll>
	<gib.forceBuildModules></gib.forceBuildModules>
	<gib.excludeTransitiveModulesPackagedAs></gib.excludeTransitiveModulesPackagedAs>
	<gib.compareToMergeBase>true</gib.compareToMergeBase>
	<gib.fetchBaseBranch>false</gib.fetchBaseBranch>
	<gib.fetchReferenceBranch>false</gib.fetchReferenceBranch>
	<gib.excludePathRegex>(?!x)x</gib.excludePathRegex>
	<gib.failOnMissingGitDir>true</gib.failOnMissingGitDir>
	<gib.failOnError>true</gib.failOnError>
</properties>
```

### gib.enabled

Can be used to disable this extension temporarily or permanently (e.g. to avoid clashes with IDE building strategy).

### gib.disableBranchComparison

Disables the comparison between `baseBranch` and `referenceBranch`. This property should be enabled if _only_ uncommitted and/or untracked files shall be detected to only build projects that have been changed since the last commit in the current branch (see `gib.uncommited` and `gib.untracked`).
The following properties are _not_ evaluated when `gib.disableBranchDiff` is enabled:
 - `gib.referenceBranch`
 - `gib.compareToMergeBase`
 - `gib.fetchReferenceBranch`
 - `gib.excludePathRegex`

### gib.uncommited

Detects changed files that have not yet been committed. This does **not** include _untracked_ files (see `git status` manual).

### gib.untracked

Detects files that are not yet tracked by git (see `git status` manual). This does **not** include _uncommitted_ files. A new file is not _untracked_ anymore after it is added to the index.

### gib.skipTestsForNotImpactedModules

In conjunction with `-am` or `gib.buildAll=true` this property disables the compilation/execution of tests for modules that have *not* been changed by adding `maven.test.skip=true`. In case a not impacted module produces a test jar just the test *execution* is disbled via `skipTests=true`.

Can be combined with `gib.argsForNotImpactedModules`.

### gib.argsForNotImpactedModules

In conjunction with `-am` or `gib.buildAll=true` this property allows adding arguments/properties for modules that have *not* been changed to futher reduce overhead, e.g. skip Checkstyle or Enforcer plugin.
Arguments have to be sparated with a single space character and values are optional. Example:
```
mvn clean install -am -Dgib.argsForNotImpactedModules='-Denforcer.skip -Dcheckstyle.skip=true'
```

Can be combined with `gib.skipTestsForNotImpactedModules`.

### gib.forceBuildModules

Defines artifact ids of modules to build forcibly, even if these modules have not been changed and/or do not depend on changed modules. Example:
```
mvn clean install -Dgib.forceBuildModules=unchanged-module-1,unchanged-module-2
```
Regular expressions are also supported in each comma separated part, e.g.:
```
mvn clean install -Dgib.forceBuildModules=unchanged-module-.*,another-module
```
Each of these modules is subject to `argsForNotImpactedModules` and `skipTestsForNotImpactedModules`.

This property has no effect in case `buildAll` is enabled.

### gib.excludeTransitiveModulesPackagedAs

Defines the packaging (e.g. `jar`) of modules that depend on changed modules but shall not be built.

One possible use case for this is mainly working in an IDE, fixing all compile errors etc. and then just quickly building the least possible amount of modules
which are needed to (hot-)deploy the changes via `mvn` on the command line.
In this scenario, by defining `-Dgib.excludeTransitiveModulesPackagedAs=jar,pom`, only the directly changed `jar` modules and the dependent `war` and/or `ear` deployment modules will be built. 

This property has no effect in case `buildAll` is enabled and an exclusion might be overriden by `gib.forceBuildModules`.

## Requirements

- Maven version 3+.
