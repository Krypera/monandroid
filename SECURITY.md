# Security Policy

Monandroid handles mining profiles, wallet addresses, pool credentials, and a packaged native runtime. Security issues are taken seriously.

## Supported versions

Because the project is still pre-release, only the latest code on the default branch should be considered supported for security fixes.

## Reporting a vulnerability

Please do not open a public GitHub issue for vulnerabilities that could expose:

- saved pool passwords
- wallet information
- diagnostics exports
- import/export backups
- code execution in the app or native runtime

If GitHub private vulnerability reporting is enabled for the repository, use that first.

If no private reporting channel is available yet, please wait for one to be published in this file rather than disclosing a sensitive issue publicly.

For non-sensitive hardening ideas, defensive improvements, or privacy suggestions, a normal issue is fine.

## What to include

- affected version or commit
- device and Android version
- reproduction steps
- expected vs actual behavior
- whether the issue requires an existing profile, export file, or diagnostics snapshot
- whether any secret material is required to reproduce

## Disclosure expectations

- Please allow time for triage and a fix before public disclosure.
- Once a fix ships, documentation and changelog notes should be updated when appropriate.
