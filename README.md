# gitflow-incremental-builder

- Can be used as a extension in Maven build or stand alone to build only changed modules compared to "origin/develop" branch.
- Returns names of maven modules which contain changes compared to "origin/develop" branch.
- Useful for multi-module maven projects using Gitflow model, where "origin/develop" is always stable.
- Don't forget to fetch first.

## Maven Usage

- Copy correctly into corresponding maven repository directory.
- Add into build:

    <build>
        ...
        <extensions>
            <extension>
                <groupId>com.vackosar.gitflowincrementalbuilder</groupId>
                <artifactId>gitflow-incremental-builder</artifactId>
                <version>1.1</version>
            </extension>
        </extensions>
    </build>

## Commandline Usage

    usage: [path to pom] [OPTIONS]
     -b,--branch <branch>                        defaults to 'HEAD'
     -k,--key <path>                             path to repo private key
     -rb,--reference-branch <reference branch>   defaults to
                                                 'refs/remotes/origin/develop'

## Example Bash Execution

    mvn -amd -pl "$(gib.sh pom.xml)" --file pom.xml

Builds compared to "origin/develop" modules containing changes and all their dependents.

## Last Release

https://github.com/vackosar/gitflow-incremental-builder/raw/master/release/gitflow-incremental-builder-1.1-bin.zip
