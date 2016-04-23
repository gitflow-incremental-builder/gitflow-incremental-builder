# gitflow-incremental-builder

A maven extension for incremental building of multi-module projects when using Git Flow.
- Builds only changed maven modules compared to reference branch in Git (e.g. origin/develop) and all their dependents.
- Useful for multi-module maven projects using Gitflow model, where "origin/develop" is always stable.
- Extension is configured using maven POM properties or JVM properties.
- Don't forget to git-fetch first.

## Usage

- [Install GIB into maven repo.](https://maven.apache.org/guides/mini/guide-3rd-party-jars-local.html)
- Add into build:
```xml
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
```

## Last Release

https://github.com/vackosar/gitflow-incremental-builder/raw/master/release/gitflow-incremental-builder-1.1.jar
