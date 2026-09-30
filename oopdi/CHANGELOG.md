# Changelog

## 1.0.0 (2026-09-30)

- feat!: require explicit startup() before bean access
- fix: build startup banner from explicit literals matching README
- fix: build startup banner from explicit literals matching README
- feat: print startup banner on container creation
- fix: close shutdown races and align validation with runtime failures
- feat: align startup validation with runtime semantics
- revert: drop premature deprecation ceremony (no external users yet)
- feat: add opt-in startup graph validation
- fix: dedicated error types, explicit failures, templated messages
- fix: harden thread and lifecycle hygiene leftovers
- fix: harden leftover robustness gaps (atomic cache fill, optional primitives)
- feat: share generated proxy classes across containers
- fix: destroy REQUEST beans at request-chain end
- fix: isolate REQUEST scope state per container
- fix: harden shutdown with state handling and best-effort destruction
- fix: make shared per-scope instance cache thread-safe
- feat!: add background metadata warmup with MetadataMode
- fix: restore single-constructor validation as MultipleConstructors

## 0.3.0 (2026-09-20)

- Merge branch 'main' of https://github.com/oopexpert/oopdi.git into main
- feat: metadata caching

## 0.2.0 (2026-09-01)

- feat: merge release workflows and publish to GitHub Packages

## [0.1.0](https://github.com/oopexpert/oopdi/compare/0.0.4...v0.1.0) (2026-09-01)


### Features

* adopt release-please for automated versioning ([f385c65](https://github.com/oopexpert/oopdi/commit/f385c659e38538a757b62ee4909177999e066767))


### Bug Fixes

* cache resolved real object for GLOBAL and THREAD scoped proxies ([fcf1781](https://github.com/oopexpert/oopdi/commit/fcf1781cd914566266e47148dd98374e526b820c))
* load classpath-scanned candidates without initializing them ([0dbc5da](https://github.com/oopexpert/oopdi/commit/0dbc5da44330dcb8bcc7ee3a9e5c95b32915b5ce))
* narrow reflective field access to actually-injected fields ([5f7235b](https://github.com/oopexpert/oopdi/commit/5f7235b273b3b8d0f6194423427f785a9c564a38))
* validate eligibility before proxy or constructor invocation ([a39a0fc](https://github.com/oopexpert/oopdi/commit/a39a0fc0a2cd3c765143e3f70af39bcc82e0523e))

## Changelog

All notable changes to this project will be documented in this file. See
[Conventional Commits](https://www.conventionalcommits.org/) for commit
guidelines used to generate this file.
