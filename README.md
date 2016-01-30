# gitflow-incremental-builder

- Returns names of maven modules which contain changes compared to origin/develop branch.
- Useful for multi-module maven projects using Gitflow model, where origin/develop is always stable.

## Example Bash Execution

``` mvn -pl "$(gib.sh pom.xml)" pom.xml ```

Builds compared to origin/develop modules containing changes.

## Last Release

https://github.com/vackosar/gitflow-incremental-builder/blob/master/target/gitflow-incremental-builder-1.0-SNAPSHOT-bin.zip
