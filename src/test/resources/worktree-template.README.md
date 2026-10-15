# worktree-template.zip

Used by `DifferentFilesWorktreeTest` (see issue #1238: linked worktrees of a *bare* repository, plus the regular case).

## Content

| Path         | Description                                                                                                             |
|--------------|-------------------------------------------------------------------------------------------------------------------------|
| `bare.git`   | Bare repo with branches `master` (`pom.xml` + `child1`/`child2` modules) and `feature` (adds `child1/feature-file.txt`) |
| `bare-wt`    | Linked worktree of `bare.git`, checked out at `feature`                                                                 |
| `normal`     | Regular repo with the same history, checked out at `master`                                                             |
| `normal-wt`  | Linked worktree of `normal`, checked out at `feature`                                                                   |

The links between a repo and its worktree are **absolute paths** (`*-wt/.git` and `<gitdir>/worktrees/*-wt/gitdir`).
They are only valid for the location where the zip was created, so the test rewrites exactly these files after unzipping.
Everything else is untouched, real git output.

## How it was created

With git 2.56 (any recent version should do). Fixed author/committer data keeps the commits reproducible.

```bash
export GIT_CONFIG_NOSYSTEM=1 HOME=/tmp/wtt-home
export GIT_AUTHOR_NAME=test GIT_AUTHOR_EMAIL=test@example.com GIT_COMMITTER_NAME=test GIT_COMMITTER_EMAIL=test@example.com
export GIT_AUTHOR_DATE="2020-01-01T00:00:00Z" GIT_COMMITTER_DATE="2020-01-01T00:00:00Z"
mkdir -p /tmp/wtt-home /tmp/wtt && cd /tmp/wtt

# 1. regular repo with the two branches
git init -b master normal && cd normal
#    create pom.xml (packaging pom, modules child1 + child2) and child1/pom.xml + child2/pom.xml (parent: wt-test:parent:1)
git add . && git commit -m "initial"
git checkout -b feature
echo changed > child1/feature-file.txt
git add . && git commit -m "feature"
git checkout master && cd ..

# 2. bare clone (without origin) + linked worktrees for both repos
git clone --bare normal bare.git
git -C bare.git remote remove origin
git -C bare.git worktree add ../bare-wt feature
git -C normal worktree add ../normal-wt feature

# 3. pack (-X: no extra file attributes)
zip -r -X worktree-template.zip bare.git bare-wt normal normal-wt
```

Copy the resulting zip to `src/test/resources`.
