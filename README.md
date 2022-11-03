# gitflow-incremental-builder (GIB)
[![GitHub license](https://img.shields.io/github/license/gitflow-incremental-builder/gitflow-incremental-builder.svg)](https://github.com/gitflow-incremental-builder/gitflow-incremental-builder/blob/main/LICENSE)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.gitflow-incremental-builder/gitflow-incremental-builder)](https://maven-badges.herokuapp.com/maven-central/io.github.gitflow-incremental-builder/gitflow-incremental-builder)
[![CI](https://github.com/gitflow-incremental-builder/gitflow-incremental-builder/workflows/CI/badge.svg)](https://github.com/gitflow-incremental-builder/gitflow-incremental-builder/actions?query=workflow%3A%22CI%22+branch%3Amain)
[![Codacy Badge](https://app.codacy.com/project/badge/Grade/2a74eedd5e0c4694ac8cf44b315cfb5a)](https://www.codacy.com/gh/gitflow-incremental-builder/gitflow-incremental-builder/dashboard?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=gitflow-incremental-builder/gitflow-incremental-builder&amp;utm_campaign=Badge_Grade)
[![Codacy Badge](https://app.codacy.com/project/badge/Coverage/2a74eedd5e0c4694ac8cf44b315cfb5a)](https://www.codacy.com/gh/gitflow-incremental-builder/gitflow-incremental-builder/dashboard?utm_source=github.com&utm_medium=referral&utm_content=gitflow-incremental-builder/gitflow-incremental-builder&utm_campaign=Badge_Coverage)
[![Supported JVM Versions](https://img.shields.io/badge/JVM-11_17_19_20--ea-brightgreen.svg?logo=Java)](https://github.com/gitflow-incremental-builder/gitflow-incremental-builder/actions?query=workflow%3A%22CI%22+branch%3Amain)
[![GitHub Discussions](https://img.shields.io/github/discussions/gitflow-incremental-builder/gitflow-incremental-builder)](https://github.com/gitflow-incremental-builder/gitflow-incremental-builder/discussions)

A maven extension for incremental building of multi-module projects when using [feature branches (Git Flow)](http://nvie.com/posts/a-successful-git-branching-model/).
Builds or tests only changed maven modules compared to reference branch in Git (e.g. origin/develop) and all their dependents.<br/>
Powered by [JGit](https://www.eclipse.org/jgit/).

This extension is **not limited to Git Flow setups!** The [extensive configuration options](#configuration) provide support for many other branch setups and/or use cases. 

## Table of Contents

- [Requirements](#requirements)

- [Usage](#usage)
  - [Usage as a Maven extension](#usage-as-a-maven-extension)
  - [Usage as a Maven plugin](#usage-as-a-maven-plugin)
  - [Disable in IDE](#disable-in-ide)

- [Example](#example)

- [Configuration](#configuration)
  - [gib.help](#gibhelp)
  - [gib.disable](#gibdisable)
  - [gib.disableIfBranchMatches](#gibdisableifbranchmatches)
  - [gib.disableIfReferenceBranchMatches](#gibdisableifreferencebranchmatches)
  - [gib.disableBranchComparison](#gibdisablebranchcomparison)
  - [gib.referenceBranch](#gibreferencebranch)
  - [gib.fetchReferenceBranch](#gibfetchreferencebranch)
  - [gib.baseBranch](#gibbasebranch)
  - [gib.fetchBaseBranch](#gibfetchbasebranch)
  - [gib.compareToMergeBase](#gibcomparetomergebase)
  - [gib.uncommitted](#gibuncommitted)
  - [gib.untracked](#gibuntracked)
  - [gib.skipIfPathMatches](#gibskipifpathmatches)
  - [gib.excludePathsMatching](#gibexcludepathsmatching)
  - [gib.includePathsMatching](#gibincludepathsmatching)
  - [gib.buildAll](#gibbuildall)
  - [gib.buildAllIfNoChanges](#gibbuildallifnochanges)
  - [gib.buildDownstream](#gibbuilddownstream)
  - [gib.buildUpstream](#gibbuildupstream)
  - [gib.buildUpstreamMode](#gibbuildupstreammode)
  - [gib.skipTestsForUpstreamModules](#gibskiptestsforupstreammodules)
  - [gib.argsForUpstreamModules](#gibargsforupstreammodules)
  - [gib.argsForDownstreamModules](#gibargsfordownstreammodules)
  - [gib.forceBuildModules](#gibforcebuildmodules)
  - [gib.excludeDownstreamModulesPackagedAs](#gibexcludedownstreammodulespackagedas)
  - [gib.disableSelectedProjectsHandling](#gibdisableselectedprojectshandling)
  - [gib.failOnMissingGitDir](#failonmissinggitdir)
  - [gib.failOnError](#gibfailonerror)
  - [gib.logImpactedTo](#giblogimpactedto)

- [Explicitly selected projects](#explicitly-selected-projects)
  - [mvn -pl](#mvn--pl)
  - [mvn -f and others](#mvn--f-and-others)
  - [mvn -N](#mvn--N)

- [BOM support](#bom-support)

- [Test only changes](#test-only-changes)

- [Authentication](#authentication)
  - [HTTP](#http)
  - [SSH](#ssh)

## Requirements

To be able to use GIB, your project must use:

- **Apache Maven** build tool, version **3.8.6** is recommended
  - The minimum Maven version is 3.6.3 due to [MNG-6580](https://issues.apache.org/jira/browse/MNG-6580)

- **Git** version control

- **Java 11** (or higher)

ℹ️ If you need support for Maven <= 3.6.2 and/or Java < 11, you might want to consider using [version 3 of GIB](https://github.com/gitflow-incremental-builder/gitflow-incremental-builder/tree/3.x).

## Usage

There are two usage scenarios of GIB:

### Usage as a Maven extension

Add to (root) `pom.xml`:
```xml
<build>
    <extensions>
        <extension>
            <groupId>io.github.gitflow-incremental-builder</groupId>
            <artifactId>gitflow-incremental-builder</artifactId>
            <version>4.2.0</version>
        </extension>
    </extensions>
    <!-- ... -->
</build>
```
or use [`.mvn/extensions.xml`](https://maven.apache.org/ref/3.8.6/maven-embedder/core-extensions.html).

The [Configuration](#configuration) can then be added via project or system properties.

### Usage as a Maven plugin

Add to (root) `pom.xml`:
```xml
<build>
    <plugins>
        <plugin>
            <groupId>io.github.gitflow-incremental-builder</groupId>
            <artifactId>gitflow-incremental-builder</artifactId>
            <version>4.2.0</version>
            <extensions>true</extensions>
            <configuration>
                <!-- ... -->
            </configuration>
        </plugin>
        <!-- ... -->
    </plugins>
    <!-- ... -->
</build>
```

The [Configuration](#configuration) can then be added to the `configuration` block of the plugin.

:warning: GIB comes with a "fake" mojo (or goal). **Do _not_ try to execute this fake mojo!**<br/>
It only exists to generate a plugin descriptor so that you can see the parameters nicely in your IDE or via `maven-help-plugin`.<br/>
Because of this limitation, you have to use the general `<configuration>` section of the plugin instead of any `<execution>` block.

When defined as a plugin, GIB will still do its work as an extension (see `<extensions>true</extensions>`).<br/>
The plugin definition is merely a "shell" to provide `<profile>`-support, better visibility of parameters and works around 3rd-party issues like
[`versions-maven-plugin`: Support for reporting new extenions versions](https://github.com/mojohaus/versions-maven-plugin/issues/74).

### Disable in IDE

As IDEs like IntelliJ IDEA or Eclipse usually apply their own custom strategy to building changed modules,
this extension should be disabled in such environments to avoid inconsistencies or errors (e.g. see [issue 49](../../issues/49)).

See [gib.disable](#gibdisable) in the configuration section.

:information_source: If using IntelliJ IDEA, version **2019.3.1** or higher is required for GIB 3.8+ (even if disabled).
See [IDEA-200272](https://youtrack.jetbrains.com/issue/IDEA-200272) and [issue 91](../../issues/91) for more details.

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
                <groupId>io.github.gitflow-incremental-builder</groupId>
                <artifactId>gitflow-incremental-builder</artifactId>
                <version>4.2.0</version>
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
Thus incremental does not build and test anything (see [gib.buildAllIfNoChanges](#gibbuildallifnochanges) for an alternate mode):
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
    <gib.help>false</gib.help>                                                         <!-- or -Dgib.h=...     -->
    <gib.disable>false</gib.disable>                                                   <!-- or -Dgib.d=...     -->
    <gib.disableIfBranchMatches></gib.disableIfBranchMatches>                          <!-- or -Dgib.dibm=...  -->
    <gib.disableIfReferenceBranchMatches></gib.disableIfReferenceBranchMatches>        <!-- or -Dgib.dirbm=... -->
    <gib.disableBranchComparison>false</gib.disableBranchComparison>                   <!-- or -Dgib.dbc=...   -->
    <gib.referenceBranch>refs/remotes/origin/develop</gib.referenceBranch>             <!-- or -Dgib.rb=...    -->
    <gib.fetchReferenceBranch>false</gib.fetchReferenceBranch>                         <!-- or -Dgib.frb=...   -->
    <gib.baseBranch>HEAD</gib.baseBranch>                                              <!-- or -Dgib.bb=...    -->
    <gib.fetchBaseBranch>false</gib.fetchBaseBranch>                                   <!-- or -Dgib.fbb=...   -->
    <gib.compareToMergeBase>true</gib.compareToMergeBase>                              <!-- or -Dgib.ctmb=...  -->
    <gib.uncommitted>true</gib.uncommitted>                                            <!-- or -Dgib.uc=...    -->
    <gib.untracked>true</gib.untracked>                                                <!-- or -Dgib.ut=...    -->
    <gib.skipIfPathMatches></gib.skipIfPathMatches>                                    <!-- or -Dgib.sipm=...  -->
    <gib.excludePathsMatching></gib.excludePathsMatching>                              <!-- or -Dgib.epm=...   -->
    <gib.includePathsMatching></gib.includePathsMatching>                              <!-- or -Dgib.ipm=...   -->
    <gib.buildAll>false</gib.buildAll>                                                 <!-- or -Dgib.ba=...    -->
    <gib.buildAllIfNoChanges>false</gib.buildAllIfNoChanges>                           <!-- or -Dgib.bainc=... -->
    <gib.buildDownstream>always</gib.buildDownstream>                                  <!-- or -Dgib.bd=...    -->
    <gib.buildUpstream>derived</gib.buildUpstream>                                     <!-- or -Dgib.bu=...    -->
    <gib.buildUpstreamMode>changed</gib.buildUpstreamMode>                             <!-- or -Dgib.bum=...   -->
    <gib.skipTestsForUpstreamModules>false</gib.skipTestsForUpstreamModules>           <!-- or -Dgib.stfum=... -->
    <gib.argsForUpstreamModules></gib.argsForUpstreamModules>                          <!-- or -Dgib.afum=...  -->
    <gib.argsForDownstreamModules></gib.argsForDownstreamModules>                      <!-- or -Dgib.afdm=...  -->
    <gib.forceBuildModules></gib.forceBuildModules>                                    <!-- or -Dgib.fbm=...   -->
    <gib.excludeDownstreamModulesPackagedAs></gib.excludeDownstreamModulesPackagedAs>  <!-- or -Dgib.edmpa=... -->
    <gib.disableSelectedProjectsHandling>false</gib.disableSelectedProjectsHandling>   <!-- or -Dgib.dsph=...  -->
    <gib.failOnMissingGitDir>true</gib.failOnMissingGitDir>                            <!-- or -Dgib.fomgd=... -->
    <gib.failOnError>true</gib.failOnError>                                            <!-- or -Dgib.foe=...   -->
    <gib.logImpactedTo></gib.logImpactedTo>                                            <!-- or -Dgib.lit=...   -->
</properties>
```

Each property can also be set via `-D` on the command line and to reduce typing to a minimum, each property has a short name (see code block above).

E.g. `-Dgib.d=true` yields the same result as `-Dgib.disable=true`.

Properties that support the value `true` can be specified _without_ a value, e.g. `-Dgib.disable` is the same as `-Dgib.disable=true`.

System properties (`-D`) take precedence over project properties from the POM and secondarily to that a full name takes precedence over the respective short name.<br/>
Short names can only be used as system properties.

If GIB is [used as a **plugin**](#usage-as-a-maven-plugin) (instead of an extension), the same properties _can_ still be used but it is recommended
to put the properties into the plugin `<configuration>`-section, but _without_ the `gib.`-prefix.<br/>
E.g. instead of:
```xml
<properties>
    <gib.uncommitted>true</gib.uncommitted>
</properties>
```
use:
```xml
<configuration>
    <uncommitted>true</uncommitted>
</configuration>
```
Just like for regular plugins, a plugin config property that is set to a _fixed_ value **cannot be changed via the respective system property** (here: `-Dgib.uncommitted=false`).

### gib.help

Logs the available properties etc.

Note: Independent of `gib.disable`.

Since: 3.9.0

### gib.disable

Can be used to disable this extension temporarily or permanently (e.g. to avoid clashes with IDE building strategy).

Since: 3.11.2 (replaces previously used `enabled` property)

### gib.disableIfBranchMatches

Can be used to disable this extension on certain branches (e.g. `main|develop`). By default, GIB runs on all branches.

Since: 3.11.0

Was renamed from `disableIfBranchRegex` in 3.13.0.

### gib.disableIfReferenceBranchMatches

Can be used to disable this extension on certain _reference_ branches (e.g. `origin/(main|develop)`). By default, GIB runs for all reference branches.

See also: [gib.referenceBranch](#gibreferencebranch)

Since: 3.14.2

### gib.disableBranchComparison

Disables the comparison between `baseBranch` and `referenceBranch`. This property should be enabled if _only_ uncommitted and/or untracked files shall be detected to only build projects that have been changed since the last commit in the current branch (see `gib.uncommitted` and `gib.untracked`).
The following properties are _not_ evaluated when `gib.disableBranchComparison` is enabled:
- `gib.referenceBranch`
- `gib.compareToMergeBase`
- `gib.fetchReferenceBranch`

### gib.referenceBranch

The branch to compare `baseBranch` to. You can use the simple branch name to compare against a local branch, e.g. `develop`.

If you want to compare to a _remote_ branch you need to use the prefix `refs/remotes/<remoteName>/`, e.g. `refs/remotes/origin/develop`.

You can also use a tag name, e.g. `refs/tags/myTag` or just `myTag`.

### gib.fetchReferenceBranch

Fetches the `referenceBranch` from the remote repository.

You _must_ use the prefix `refs/remotes/<remoteName>/`, e.g. `refs/remotes/origin/develop`, so that GIB can determine the remote repo name.

Alternatively, since 3.15.0, you can also fetch tags via `refs/tags/<tagName>`.

See also: [Authentication](#authentication)

### gib.baseBranch

The branch that is compared to `referenceBranch`. Usually just the current `HEAD`.

See also: [gib.referenceBranch](#gibreferencebranch)

### gib.fetchBaseBranch

Fetches the `baseBranch` from the remote repository.

See also:

- [Authentication](#authentication)
- [gib.fetchReferenceBranch](#gibfetchreferencebranch)

### gib.compareToMergeBase

Controls whether or not to the [merge-base](https://git-scm.com/docs/git-merge-base) mechanism to compare the branches.

### gib.uncommitted

Detects changed files that have not yet been committed. This does **not** include _untracked_ files (see `git status` manual).

### gib.untracked

Detects files that are not yet tracked by git (see `git status` manual). This does **not** include _uncommitted_ files. A new file is not _untracked_ anymore after it is added to the index.

### gib.skipIfPathMatches

Can be used to skip this extension on certain changes. By default, no changed file results in GIB being skipped.

This property is helpful for more complex project setups in which not all submodules depend on the root module (= are not direct or indirect children of the root module).<br/>
In such a case, GIB will assign a change to e.g. `.github/workflows/ci.yml` to the root module, building all modules that depend on the root module.<br/>
Consequently, this will **not** include the other modules that are referenced top-down from root, but not bottom-up via `<parent>...</parent>`.<br/>
One example of such modules are [the independent-projects of Quarkus](https://github.com/quarkusio/quarkus/tree/1.11.3.Final/independent-projects).

By setting this property to e.g. `\.github[/\\].*` you can tell GIB that this path has a _global_ effect and that it's pointless to attempt an incremental build.

:information_source: Use `[/\\]` instead of just `/` to also cover Windows path separators.

Since: 3.12.2

### gib.excludePathsMatching

Can be used to exclude certain changed files from being detected as changed, reducing the number of modules to build. By default, nothing is excluded.

The regular expression does _not_ need to describe the entire (absolute) path, but only the relevant part _inside_ the git repository context. Example:
```
/tmp/repo/excluded/some-file.txt
```
will be excluded when using `-Dgib.excludePathsMatching=.*excluded.*` or `-Dgib.excludePathsMatching=.*some-file\..*` etc., but is _not_ excluded when adding to the regular expression anything _outside_ of the git repository context like `/tmp/repo` or `repo`.

This the opposite of [gib.includePathsMatching](#gibincludepathsmatching) which can be combined with this property, but `gib.excludePathsMatching` will take precedence.

:information_source: Use `[/\\]` instead of just `/` to also cover Windows path separators.

Was renamed from `excludePathRegex` in 3.13.0.

### gib.includePathsMatching

Can be used to include only certain changed files from being detected as changed, reducing the number of modules to build. By default, everything is included.

This the opposite of [gib.excludePathsMatching](#gibexcludepathsmatching) which can be combined with this property, but `gib.excludePathsMatching` will take precedence.

See [gib.excludePathsMatching](#gibexcludepathsmatching) for general path matching rules.

:information_source: Use `[/\\]` instead of just `/` to also cover Windows path separators.

Since: 3.10.0

Was renamed from `includePathRegex` in 3.13.0.

### gib.buildAll

Builds all modules, including upstream modules (see also `gib.buildUpstream`). Can be used to (temporarily) override the reduction of modules to build.

Can be combined/useful with `gib.skipTestsForUpstreamModules` and/or `gib.argsForUpstreamModules`.

### gib.buildAllIfNoChanges

In case no changes are detected, GIB will (by default) just invoke goal `validate` in the "current project" (usually the root module), skipping any submodules.

If this property is enabled, GIB will instead auto-activate [gib.buildAll](#gibbuildall) for all modules and will leave the goals untouched.

This property is ignored when [explicitly selected projects](#explicitly-selected-projects) are involved.

Since: 3.9.2

### gib.buildDownstream

Controls whether or not to build downstream modules (= modules that depend on the modules GIB detected as changed):

- `always` or `true` (default value): always build downstream modules (depends on `-amd` when `-pl` is used)
- `derived`: only build downstream modules if `mvn -amd` is called
- `never` or `false`: never build downstream modules

Since: 3.8

### gib.buildUpstream

Controls whether or not to build upstream modules (= dependencies and parents of the modules GIB has determined to build):

- `always` or `true`: always build upstream modules (depends on `-am` when `-pl` is used)
- `derived` (default value): only build upstream modules if `mvn -am` is called
- `never` or `false`: never build upstream modules

See also `gib.buildUpstreamMode`.

Since: 3.8

### gib.buildUpstreamMode

This property controls which upstream modules to build (_if_ at all building upstream modules, see `gib.buildUpstream`):

- `changed` (default value): selects only upstream modules of the _directly changed_ modules
- `impacted`: like `changed` but also selects upstream modules of modules _that depend on_ the directly changed modules (in other words: upstream modules of the downstream modules of the changed modules)

`changed` is a subset of `impacted`.

`impacted` may seem odd at first, but it does come in handy in certain scenarios, e.g. a Jenkins PR job that locally merges target branch into the PR branch before building.
Here it might be required to freshly compile upstream modules of not directly changed modules to avoid compile errors or test failures which originate from the target branch.

Both strategies can and usually should be combined with `gib.skipTestsForUpstreamModules` and/or `gib.argsForUpstreamModules`.

This property is ignored when [explicitly selected projects](#explicitly-selected-projects) are involved.

Note: _Before_ 3.8, GIB did non have this property and was implicitly applying the `impacted` strategy, see also [issue 44](../../issues/44).

Since: 3.8

### gib.skipTestsForUpstreamModules

This property disables the compilation/execution of tests for upstream modules by adding `maven.test.skip=true`. In case an upstream module produces a test jar just the test _execution_ is disabled via `skipTests=true`.

See `gib.buildUpstream` or `gib.buildAll` to learn when upstream modules are built.

Can be combined with `gib.argsForUpstreamModules`.

### gib.argsForUpstreamModules

This property allows adding arbitrary arguments/properties for upstream modules to further reduce overhead, e.g. skip Checkstyle or Enforcer plugin.
Arguments have to be sparated with a single space character and values are optional. Example:

```
mvn clean install -am -Dgib.argsForUpstreamModules='enforcer.skip checkstyle.skip=true'
```
(notice the missing `-D`)

See `gib.buildUpstream` or `gib.buildAll` to learn when upstream modules are built.

Can be combined with `gib.skipTestsForUpstreamModules`.

### gib.argsForDownstreamModules

This property allows adding arbitrary arguments/properties for downstream modules to e.g. run them with a smaller testset than the directly changed modules. Only _unchanged_ downstream modules are affected by this setting.
Arguments have to be sparated with a single space character and values are optional. Example:

```
mvn clean install -am -Dgib.argsForDownstreamModules='testprofile=small'
```
(notice the missing `-D`)

See `gib.buildDownstream` or `gib.buildAll` to learn when upstream modules are built.

Since: 4.2.0

### gib.forceBuildModules

Defines artifact ids of modules to build forcibly, even if these modules have not been changed and/or do not depend on changed modules. Example:

```
mvn clean install -Dgib.forceBuildModules=unchanged-module-1,unchanged-module-2
```

Regular expressions are also supported in each comma separated part, e.g.:

```
mvn clean install -Dgib.forceBuildModules=unchanged-module-.*,another-module
```

Each of these modules is subject to `argsForUpstreamModules` and `skipTestsForUpstreamModules`.

This property has no effect in case `buildAll` is enabled.

Additionally (since 3.14.4), forced modules can be defined _conditionally_, e.g.:

```
changed-module=unchanged-module-1|unchanged-module-2
```

will build `unchanged-module-1` _and_ `unchanged-module-2` only if `changed-module` was changed (or depends on changed modules).

There can be multiple such key=value pairs, fully supporting regular expressions and you can mix both flavors, e.g.:

```
another-module, changed-module=unchanged-module-1|unchanged-module-2, .*-core-module=lib-.*-module, test-.*
```

Note: `,` and `=` are reserved delimiters and can therefore _not_ be used in artifact id strings/patterns.

### gib.excludeDownstreamModulesPackagedAs

Defines the packaging (e.g. `jar`) of modules that depend on changed modules but shall not be built.

One possible use case for this is mainly working in an IDE, fixing all compile errors etc. and then just quickly building the least possible amount of modules
which are needed to (hot-)deploy the changes via `mvn` on the command line.
In this scenario, by defining `-Dgib.excludeDownstreamModulesPackagedAs=jar,pom`, only the directly changed `jar` modules and the dependent `war` and/or `ear`
deployment modules will be built.

This property has no effect in case `buildAll` is enabled and an exclusion might be overridden by `gib.forceBuildModules`.

### gib.disableSelectedProjectsHandling

Disables special handling of [explicitly selected projects](#explicitly-selected-projects) (-pl, -f etc.).

This can come in handy if you select just one module with `-pl` but you only want to have it built _fully_ (with tests etc.)
if the selected module itself is changed or one of its (non-selected) upstream modules.

Since: 3.12.0

### gib.failOnMissingGitDir

Controls whether or not to fail on missing `.git` directory.

### gib.failOnError

Controls whether or not to fail on any error.

### gib.logImpactedTo

Defines an optional logfile which GIB shall write all "impacted" modules to. Each line represents the base directory of a changed module
or a downstream module of a changed module.

GIB overwrites the file if it already exists and will create an empty file in case no changes are detected
or only [explicitly selected projects](#explicitly-selected-projects) are present.

Since: 3.10.1

## Explicitly selected projects

By default, GIB tries not to interfere with any projects/modules that have been selected explicitly by the user.

The details are described in the following subsections. This special handling can be disabled via [gib.disableSelectedProjectsHandling](#gibdisableSelectedProjectsHandling).

### mvn -pl

Since 3.10.0, special rules apply when `mvn -pl ...` (or `--projects ...`) is used:

- _Every_ such "preselected" project is _always_ built, including tests etc., **regardless of being changed or not!**

- Downstream projects of these selected projects are built if:
  - `-amd` (`--also-make-dependents`) is used
  - _and_:
    - [`buildDownstream`](#gibbuilddownstream) is _not_ `never` or `false`
    - _or_ [`buildAll`](#gibbuildall) is enabled

- Upstream projects of these selected projects are built if:
  - `-am` (`--also-make`) is used
  - _and_:
    - [`buildUpstream`](#gibbuildUpstream) is _not_ `never` or `false`
    - _or_ [`buildAll`](#gibbuildall) is enabled

Other properties/features are applied as usual to the resulting subset of modules/projects.

Since 3.10.1, "deselected" projects (`mvn -pl !...`) that contain changes will _not_ be built, but their up- and downstream projects will be built
(if not also deselected).

### mvn -f and others

Since 3.10.0, GIB will _always_ build a "leaf module" that is selected via `mvn -f ...` (or `--file ...`), **regardless of being changed or not!**

A "leaf module" is a module without submodules.

The same applies if the current directory is changed from the root directory of the multi-module-project to the leaf module subdirectory via `cd`.

In contrast, a module _with_ submodules that is selected via `-f` or via `cd` is subject to the regular change detection rules (unless [`-pl`](#mvn--pl) or [`-N`](#mvn--N) is added).

### mvn -N

Since 3.10.2, GIB will _always_ build a "node module" when non-recursive build is activated via `mvn -N` (or `--non-recursive`), **regardless of being changed or not!**

A "node module" is a module _with_ submodules.

## BOM support

Maven does not consider modules importing a BOM (bill of material) via `dependencyManagement` as downstream modules of that BOM module.

This effectively means that (without GIB) you **cannot** just change e.g. a dependency version in the BOM and run `mvn -pl bom -amd` as this will only build `bom`.

To close this "gap", GIB adds downstream detection for BOM modules so that your Dependabot PRs or similar can trigger a proper incremental builds.

## Test only changes

If a module contains changes only within `src/test`, GIB will **not** build any of its downstream modules _unless_ that module defines a
[test-jar goal and those downstream modules are depending on that jar](https://maven.apache.org/guides/mini/guide-attached-tests.html#guide-to-using-attached-tests).

## Authentication

When using `gib.fetchBaseBranch` or `gib.fetchReferenceBranch`, GIB provides basic support to authenticate against a possibly protected remote repository.

### HTTP

For HTTP(S), GIB will query the credentials from the local native Git executable via [`git credential fill`](https://git-scm.com/docs/git-credential).<br/>
These credentials are then forwarded to JGit and are not persisted in any way. GIB will only cache the credentials _transiently_ for a very short time and will actively remove them as soon as possible.<br/>
See also [HttpDelegatingCredentialsProvider](../main/src/main/java/io/github/gitflowincrementalbuilder/jgit/HttpDelegatingCredentialsProvider.java).

Since `git credential fill` will trigger all configured [credential helpers](https://git-scm.com/docs/gitcredentials) (if any), you _might_ see a popup dialog box asking for credentials.<br/>
This only happens in case the respective helper was _not_ able to provide the credentials. Such a dialog box is _not_ created by GIB, instead it is spawned by a configured credential helper!

As GIB does _not_ (yet) provide a console input passthrough mechanism to native Git, console input queries by native Git are disabled. This means that if _no_ credential helper is configured, GIB will _not_ be able to fetch via HTTP(S) from a remote repo that requires authentication.

### SSH

For SSH, GIB pretty much relies on the default JGit/[JSch](http://www.jcraft.com/jsch/) mechanisms.<br/>
Your private key will be picked up automatically in case it is located in `~/.ssh/` (as `identity`, `id_rsa` or `id_dsa`).

To use a custom key, create a [SSH config](https://www.cyberciti.biz/faq/create-ssh-config-file-on-linux-unix/) in `~/.ssh/config` like the following:

```
Host git.somedomain.org
  IdentityFile ~/.ssh/my_key
```

If your key is protected by a **passphrase**, you will have to use a SSH agent (`ssh-agent` on Linux or `pageant` from `PuTTY` on Windows) and add your key(s) to it.

GIB relies on [SSH Agent Support as provided by JGit 6.0+](https://wiki.eclipse.org/JGit/New_and_Noteworthy/6.0#SSH_Agent_Support).

Hint: When using an agent, you don't need to put your key in a standard location, you don't need `~/.ssh/config` and your key is also _not required_ to be passphrase protected.
