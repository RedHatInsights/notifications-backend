@AGENTS.md

## Claude Code Instructions

### Pre-Commit Verification

Before finishing any code change, run checkstyle on modified modules. The fastest check (no compile, no tests):

```sh
./mvnw validate -pl :notifications-backend --no-transfer-progress
```

For changes spanning multiple modules, run from the root:

```sh
./mvnw validate --no-transfer-progress
```

After checkstyle passes, run the full build for affected modules to catch compile and test failures:

```sh
./mvnw clean verify -pl :notifications-backend -am --no-transfer-progress
```

### Things to Avoid

- Do not create or modify files outside the scope of the requested change.
- Do not run `./mvnw clean verify` at the root level for single-module changes -- it builds all modules and takes a long time. Use `-pl :module-name -am` to target the affected module.
