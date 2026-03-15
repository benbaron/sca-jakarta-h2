# Repo-local Maven Build Bootstrap

This repository now supports a **repo-local Maven cache** to improve reproducibility in restricted environments.

## What was added

- `.mvn/maven.config`
  - Forces Maven to use a repository-local cache at `.mvn/local-repo`.
  - Forces Maven to use `.mvn/settings.xml`.
- `.mvn/settings.xml`
  - Activates `repo-local-bootstrap` profile.
  - Checks local seeded repo (`file://${user.home}/.m2/repository`) first for both dependencies and plugins.
  - Falls back to Maven Central if available.
  - Designed so teams can swap in a corporate mirror by editing `.mvn/settings.xml` when needed.
- `scripts/bootstrap-local-m2.sh`
  - Seeds `.mvn/local-repo` from an existing Maven cache.

## Usage

1. Seed the local repo cache (optional but recommended):

   ```bash
   ./scripts/bootstrap-local-m2.sh
   ```

2. Run tests/build as normal:

   ```bash
   mvn test
   ```

## Restricted network note

If Maven Central is blocked, point `central` / `central-plugins` URLs in `.mvn/settings.xml` to your reachable mirror.

If no mirror is available, builds can only succeed when required artifacts already exist in the local seeded cache.


## Mirror fallback update

The default `.mvn/settings.xml` now includes additional public fallback repositories for both dependencies and plugins (`maven.aliyun.com` and `repo.huaweicloud.com`) after Maven Central.

In restricted networks, this gives Maven multiple fetch endpoints before failing. If your environment mandates internal mirrors only, replace these URLs with your approved mirror endpoints.


Additionally, `.mvn/settings.xml` now defines a primary mirror (`mirrorOf` = `external:*`) to route all external artifact/plugin resolution through a single endpoint first. This is useful in environments where direct access to Maven Central is blocked.


## Mirror settings files (opt-in)

Mirror routing is now opt-in via dedicated settings files:

- Default behavior (no forced mirror):

  ```bash
  mvn test
  ```

- Huawei mirror routing:

  ```bash
  mvn --settings .mvn/settings-mirror-huawei.xml test
  ```

- Aliyun mirror routing:

  ```bash
  mvn --settings .mvn/settings-mirror-aliyun.xml test
  ```

This keeps default builds portable while allowing explicit mirror routing in restricted environments.
