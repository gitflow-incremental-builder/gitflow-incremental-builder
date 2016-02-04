# gitflow-incremental-builder

- Returns names of maven modules which contain changes compared to develop branch.
- Useful for multi-module maven projects using Gitflow model, where develop is always stable.
- Don't forget to pull latest remote develop branch version to local repository!

## Example Bash Execution

``` mvn -pl "$(gib.sh pom.xml)" pom.xml ```

Builds compared to develop modules containing changes.

## Last Release

https://github.com/vackosar/gitflow-incremental-builder/raw/master/release/gitflow-incremental-builder-1.1-bin.zip
