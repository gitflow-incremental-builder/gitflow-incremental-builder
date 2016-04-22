# gitflow-incremental-builder

- A maven extension to build only modules changed compared to "origin/develop" branch.
- Useful for multi-module maven projects using Gitflow model, where "origin/develop" is always stable.
- Don't forget to fetch first.

## Maven Usage

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

Builds compared to "origin/develop" modules containing changes and all their dependents.

## Last Release

https://github.com/vackosar/gitflow-incremental-builder/raw/master/release/gitflow-incremental-builder-1.1.jar
