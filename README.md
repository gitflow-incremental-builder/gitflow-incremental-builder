# gitflow-incremental-builder (GIB)
[![GitHub license](https://img.shields.io/github/license/vackosar/gitflow-incremental-builder.svg)](https://github.com/vackosar/gitflow-incremental-builder/blob/master/LICENSE)
[![Latest Release](https://img.shields.io/github/v/release/vackosar/gitflow-incremental-builder?label=latest%20release)](https://github.com/vackosar/gitflow-incremental-builder/releases/latest)
[![Maven Central](https://img.shields.io/maven-central/v/com.vackosar.gitflowincrementalbuilder/gitflow-incremental-builder)](https://maven-badges.herokuapp.com/maven-central/com.vackosar.gitflowincrementalbuilder/gitflow-incremental-builder)
![Travis CI](https://travis-ci.org/vackosar/gitflow-incremental-builder.svg?branch=master)
[![Codacy Badge](https://api.codacy.com/project/badge/Grade/32140688527a49deb3bd45b8f3be4acf)](https://www.codacy.com/app/vackosar/gitflow-incremental-builder?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=vackosar/gitflow-incremental-builder&amp;utm_campaign=Badge_Grade)
[![Codacy Badge](https://api.codacy.com/project/badge/Coverage/32140688527a49deb3bd45b8f3be4acf)](https://www.codacy.com/app/gitflow-incremental-builder/gitflow-incremental-builder?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=vackosar/gitflow-incremental-builder&amp;utm_campaign=Badge_Coverage)

A maven extension for incremental building of multi-module projects when using [feature branches (Git Flow)](http://nvie.com/posts/a-successful-git-branching-model/).
Builds or tests only changed maven modules compared to reference branch in Git (e.g. origin/develop) and all their dependents.<br/>
Powered by [JGit](https://www.eclipse.org/jgit/).

This extension is **not limited to Git Flow setups!** The [extensive configuration options](#configuration) provide support for many other branch setups and/or use cases. 

## Table of Contents

- [Usage](#usage)
  - [Disable in IDE](#disable-in-ide)

- [Example](#example)

- [Configuration](#configuration)
  - [gib.help](#gibhelp)
  - [gib.enabled](#gibenabled)
  - [gib.disableBranchComparison](#gibdisablebranchcomparison)
  - [gib.referenceBranch](#gibreferencebranch)
  - [gib.fetchReferenceBranch](#gibfetchreferencebranch)
  - [gib.baseBranch](#gibbasebranch)
  - [gib.fetchBaseBranch](#gibfetchbasebranch)
  - [gib.useJschAgentProxy](#gibuseJschAgentProxy)
  - [gib.uncommited](#gibuncommited)
  - [gib.untracked](#gibuntracked)
  - [gib.excludePathRegex](#gibexcludePathRegex)
  - [gib.buildAll](#gibbuildall)
  - [gib.buildDownstream](#gibbuilddownstream)
  - [gib.buildUpstream](#gibbuildupstream)
  - [gib.buildUpstreamMode](#gibbuildupstreammode)
  - [gib.skipTestsForUpstreamModules](#gibskiptestsforupstreammodules)
  - [gib.argsForUpstreamModules](#gibargsforupstreammodules)
  - [gib.forceBuildModules](#gibforcebuildmodules)
  - [gib.excludeTransitiveModulesPackagedAs](#excludetransitivemodulespackagedas)

- [Authentication](#authentication)
  - [HTTP](#http)
  - [SSH](#ssh)

- [Requirements](#requirements)

## Usage

- Add into maven pom file:
```xml
<build>
    <extensions>
        <extension>
            <groupId>com.vackosar.gitflowincrementalbuilder</groupId>
            <artifactId>gitflow-incremental-builder</artifactId>
            <version>3.9.1</version>
        </extension>
    </extensions>
    ...
</build>
```

### Disable in IDE

As IDEs like IntelliJ IDEA or Eclipse usually apply their own custom strategy to building changed modules,
this extension should be disabled in such environments to avoid inconsistencies or errors (e.g. see [issue 49](../../issues/49)).

See [gib.enabled](#gibenabled) in the configuration section.

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
                <groupId>com.vackosar.gitflowincrementalbuilder</groupId>
                <artifactId>gitflow-incremental-builder</artifactId>
                <version>3.8.1</version>
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
    <gib.help>false</gib.help>                                                            <!-- or <gib.h>... -->
    <gib.enabled>true</gib.enabled>                                                       <!-- or <gib.e>... -->
    <gib.disableBranchComparison>false</gib.disableBranchComparison>                      <!-- or <gib.dbc>... -->
    <gib.referenceBranch>refs/remotes/origin/develop</gib.referenceBranch>                <!-- or <gib.rb>... -->
    <gib.fetchReferenceBranch>false</gib.fetchReferenceBranch>                            <!-- or <gib.frb>... -->
    <gib.baseBranch>HEAD</gib.baseBranch>                                                 <!-- or <gib.bb>... -->
    <gib.fetchBaseBranch>false</gib.fetchBaseBranch>                                      <!-- or <gib.fbb>... -->
    <gib.useJschAgentProxy>true</gib.useJschAgentProxy>                                   <!-- or <gib.ujap>... -->
    <gib.compareToMergeBase>true</gib.compareToMergeBase>                                 <!-- or <gib.ctmb>... -->
    <gib.uncommited>true</gib.uncommited>                                                 <!-- or <gib.uc>... -->
    <gib.untracked>true</gib.untracked>                                                   <!-- or <gib.ut>... -->
    <gib.excludePathRegex>(?!x)x</gib.excludePathRegex>                                   <!-- or <gib.epr>... -->
    <gib.buildAll>false</gib.buildAll>                                                    <!-- or <gib.ba>... -->
    <gib.buildDownstream>always</gib.buildDownstream>                                     <!-- or <gib.bd>... -->
    <gib.buildUpstream>derived</gib.buildUpstream>                                        <!-- or <gib.bu>... -->
    <gib.buildUpstreamMode>changed</gib.buildUpstreamMode>                                <!-- or <gib.bum>... -->
    <gib.skipTestsForUpstreamModules>false</gib.skipTestsForUpstreamModules>              <!-- or <gib.stfum>... -->
    <gib.argsForUpstreamModules></gib.argsForUpstreamModules>                             <!-- or <gib.afum>... -->
    <gib.forceBuildModules></gib.forceBuildModules>                                       <!-- or <gib.fbm>... -->
    <gib.excludeTransitiveModulesPackagedAs></gib.excludeTransitiveModulesPackagedAs>     <!-- or <gib.etmpa>... -->
    <gib.failOnMissingGitDir>true</gib.failOnMissingGitDir>                               <!-- or <gib.fomgd>... -->
    <gib.failOnError>true</gib.failOnError>                                               <!-- or <gib.foe>... -->
</properties>
```

Each property can also be set via `-D` on the command line and to reduce typing to a minimum, each property has a short name (see code block above).

E.g. `-Dgib.e=true` yields the same result as `-Dgib.enabled=true`.

Properties that support the value `true` can be specified _without_ a value, e.g. `-Dgib.enabled` is the same as `-Dgib.enabled=true`.

System properties (`-D`) take precedence over project properties from the POM and secondarily to that a full name takes precedence over the respective short name.

### gib.help

Logs the available properties etc.

Note: Independent of `gib.enabled`.

Since: 3.9.0

### gib.enabled

Can be used to disable this extension temporarily or permanently (e.g. to avoid clashes with IDE building strategy).

### gib.disableBranchComparison

Disables the comparison between `baseBranch` and `referenceBranch`. This property should be enabled if _only_ uncommitted and/or untracked files shall be detected to only build projects that have been changed since the last commit in the current branch (see `gib.uncommited` and `gib.untracked`).
The following properties are _not_ evaluated when `gib.disableBranchComparison` is enabled:
- `gib.referenceBranch`
- `gib.compareToMergeBase`
- `gib.fetchReferenceBranch`

### gib.referenceBranch

The branch to compare `baseBranch` to.

### gib.fetchReferenceBranch

Fetches the `referenceBranch` from the remote repository.

See also: [Authentication](#authentication)

### gib.baseBranch

The branch that is compared to `referenceBranch`. Usually just the current `HEAD`.

### gib.fetchBaseBranch

Fetches the `baseBranch` from the remote repository.

See also: [Authentication](#authentication)

### gib.useJschAgentProxy

Can be used to disable the usage of [`jsch-agent-proxy`](https://github.com/ymnk/jsch-agent-proxy) when fetching via SSH.

This might reduce overhead in case you don't use an agent at all (like `ssh-agent` or `pageant` from `PuTTY`).

See also: [SSH](#ssh) in the [Authentication](#authentication) section

Since: 3.9.1

### gib.uncommited

Detects changed files that have not yet been committed. This does **not** include _untracked_ files (see `git status` manual).

### gib.untracked

Detects files that are not yet tracked by git (see `git status` manual). This does **not** include _uncommitted_ files. A new file is not _untracked_ anymore after it is added to the index.

### gib.excludePathRegex

Can be used to exclude certain changed files from being detected as changed, reducing the number of modules to build.

The regular expression does _not_ need to describe the entire (absolute) path, but only the relevant part _inside_ the git repository context. Example:
```
/tmp/repo/blacklisted/some-file.txt
```
will be excluded when using `-Dgib.excludePathRegex=blacklisted` or `-Dgib.excludePathRegex=some-file\..*` etc., but is _not_ excluded when adding to the regular expression anything _outside_ of the git repository context like `/tmp/repo` or `repo`.

### gib.buildAll

Builds all modules, including upstream modules (see also `gib.buildUpstream`). Can be used to (temporarily) override the reduction of modules to build.

Can be combined/useful with `gib.skipTestsForUpstreamModules` and/or `gib.argsForUpstreamModules`.

### gib.buildDownstream

Controls whether or not to build downstream modules (= modules that depend on the modules GIB detected as changed):

- `always` or `true` (default value): always build downstream modules
- `derived`: only build downstream modules if `mvn -amd` is called
- `never` or `false`: never build downstream modules

Since: 3.8

### gib.buildUpstream

Controls whether or not to build upstream modules (= dependencies and parents of the modules GIB has determined to build):

- `always` or `true`: always build upstream modules
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

Note: _Before_ 3.8, GIB did non have this property and was implicity applying the `impacted` strategy, see also [issue 44](../../issues/44).

Since: 3.8

### gib.skipTestsForUpstreamModules

This property disables the compilation/execution of tests for upstream modules by adding `maven.test.skip=true`. In case an upstream module produces a test jar just the test _execution_ is disabled via `skipTests=true`.

See `gib.buildUpstream` or `gib.buildAll` to learn when upstream modules are built.

Can be combined with `gib.argsForUpstreamModules`.

### gib.argsForUpstreamModules

This property allows adding arbitrary arguments/properties for upstream modules to futher reduce overhead, e.g. skip Checkstyle or Enforcer plugin.
Arguments have to be sparated with a single space character and values are optional. Example:

```
mvn clean install -am -Dgib.argsForUpstreamModules='-Denforcer.skip -Dcheckstyle.skip=true'
```

See `gib.buildUpstream` or `gib.buildAll` to learn when upstream modules are built.

Can be combined with `gib.skipTestsForUpstreamModules`.

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

### gib.excludeTransitiveModulesPackagedAs

Defines the packaging (e.g. `jar`) of modules that depend on changed modules but shall not be built.

One possible use case for this is mainly working in an IDE, fixing all compile errors etc. and then just quickly building the least possible amount of modules
which are needed to (hot-)deploy the changes via `mvn` on the command line.
In this scenario, by defining `-Dgib.excludeTransitiveModulesPackagedAs=jar,pom`, only the directly changed `jar` modules and the dependent `war` and/or `ear` deployment modules will be built. 

This property has no effect in case `buildAll` is enabled and an exclusion might be overriden by `gib.forceBuildModules`.

## Authentication

When using `gib.fetchBaseBranch` or `gib.fetchReferenceBranch`, GIB provides basic support to authenticate against a possibly protected remote repository.

### HTTP

For HTTP(S), GIB will query the credentials from the local native Git executable via [`git credential fill`](https://git-scm.com/docs/git-credential).<br/>
These credentials are then forwarded to JGit and are not persisted in any way. GIB will only cache the credentials _transiently_ for a very short time and will actively remove them as soon as possible.<br/>
See also [HttpDelegatingCredentialsProvider](../master/src/main/java/com/vackosar/gitflowincrementalbuild/control/jgit/HttpDelegatingCredentialsProvider.java).

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

GIB then uses [`jsch-agent-proxy`](https://github.com/ymnk/jsch-agent-proxy) to get the unencrypted keys from the running agent. Please note that although `jsch-agent-proxy` also provides _write_ access, GIB will only _read_ keys/identities.<br/>
See also [AgentProxyAwareJschConfigSessionFactory](../master/src/main/java/com/vackosar/gitflowincrementalbuild/control/jgit/AgentProxyAwareJschConfigSessionFactory.java).

Hint: When using an agent, you don't need to put your key in a standard location, you don't need `~/.ssh/config` and your key is also _not required_ to be passphrase protected.

## Requirements

- Maven version 3.3.9+ is recommended (however, GIB _might_ work with Maven down to version 3.1.0)
- Project must use Git
