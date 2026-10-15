# bare-worktree-template.zip

Used by `DifferentFilesBareRepoWorktreeTest` (see issue #1238: linked worktree of a *bare* repository).

## Content

| Path       | Description                                                                                                             |
|------------|-------------------------------------------------------------------------------------------------------------------------|
| `bare.git` | Bare repo with branches `master` (`pom.xml` + `child1`/`child2` modules) and `feature` (adds `child1/feature-file.txt`) |
| `wt`       | Linked worktree of `bare.git`, checked out at `feature` (created via `git worktree add`)                                |

The links between the two are **absolute paths** (`wt/.git` and `bare.git/worktrees/wt/gitdir`).
They are only valid for the location where the zip was created, so the test rewrites exactly these two files after unzipping.
Everything else is untouched, real git output.

## How it was created

With git 2.56 (any recent version should do). Fixed author/committer data keeps the commits reproducible.

```bash
export GIT_CONFIG_NOSYSTEM=1 HOME=/tmp/bwt-home
export GIT_AUTHOR_NAME=test GIT_AUTHOR_EMAIL=test@example.com GIT_COMMITTER_NAME=test GIT_COMMITTER_EMAIL=test@example.com
export GIT_AUTHOR_DATE="2020-01-01T00:00:00Z" GIT_COMMITTER_DATE="2020-01-01T00:00:00Z"
mkdir -p /tmp/bwt-home /tmp/bwt && cd /tmp/bwt

# 1. regular repo with the two branches
git init -b master src && cd src
#    create pom.xml (packaging pom, modules child1 + child2) and child1/pom.xml + child2/pom.xml (parent: bare-wt:parent:1)
git add . && git commit -m "initial"
git checkout -b feature
echo changed > child1/feature-file.txt
git add . && git commit -m "feature"
git checkout master && cd ..

# 2. bare clone (without origin) + linked worktree
git clone --bare src bare.git
git -C bare.git remote remove origin
git -C bare.git worktree add ../wt feature

# 3. pack (-X: no extra file attributes)
rm -rf src
zip -r -X bare-worktree-template.zip bare.git wt
```

Copy the resulting zip to `src/test/resources`.
