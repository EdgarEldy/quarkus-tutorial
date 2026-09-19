## Branch

<!-- e.g. feature/core-architecture -->

## Task checklist

<!-- Copy the relevant section's checklist from README.md and check off each item. -->

- [ ]

## Commit summary

<!-- One line per group of commits, summarizing what changed and why. -->

## Test checklist

- [ ] Unit tests pass
- [ ] Integration tests pass (where the branch's checklist calls for them)
- [ ] `mvn verify` is green on the whole project

## Code review checklist

- [ ] No business logic in JAX-RS resources
- [ ] Success responses are wrapped in `ApiResponse<T>`, errors are RFC 9457 Problem Details (`HttpProblem`), never `ApiResponse` on an error path
- [ ] Permission checks are declarative (`@PermissionsAllowed`), no manual permission `if`
- [ ] Every `@CacheResult` has its matching `@CacheInvalidate` on the write operations
- [ ] Every RBAC mutation calls `AuditLogger`
- [ ] The database schema only changes through a Flyway migration
- [ ] No manual Testcontainers setup (Dev Services only)
- [ ] Commits are atomic (one commit per file added or modified), follow Conventional Commits, no em dash, no Claude/Anthropic mention
