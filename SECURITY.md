# Security Policy

## Supported Versions

SubSloth is developed on `main` and distributed through its tagged releases
(builds published from CI). Security fixes land on `main` and are included in
the next release; older snapshots are not maintained.

## Reporting a Vulnerability

Please do not report security vulnerabilities through public GitHub issues,
discussions, or pull requests.

Preferred channel: **GitHub private vulnerability reporting** — open the
repository's **Security** tab and choose "Report a vulnerability". The report
is visible only to the maintainer.

Alternative: email the maintainer at `krzysztof.nirski+github@gmail.com` with
the subject prefix `[subsloth-security]`.

Please include, when possible:

- the affected surface (Android app, desktop app, web production tier, or
  shared Kotlin code),
- the affected version or commit,
- reproduction steps or a proof of concept,
- impact assessment, and
- any known mitigation or workaround.

## What to Expect

Reports are acknowledged and triaged as time allows. Coordinated disclosure is
appreciated: please allow a fix to ship before publishing details. Reporters
are credited in the release notes unless they prefer to stay anonymous.

## Scope

In scope: the application code in this repository, including its handling of
stored credentials, session state, and media URLs.

Out of scope: vulnerabilities in third-party dependencies (report those
upstream) and issues that require a rooted/compromised device or physical
access.
