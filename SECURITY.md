# Security policy

## Supported versions

Security fixes are applied to the latest code on the default branch. This project is an early MVP and is not approved for handling live trading credentials or executing trades.

## Reporting a vulnerability

Please do not publish API keys, account identifiers, exploit details or sensitive personal data in a public issue. Contact the repository owner privately through GitHub's security reporting feature where available. Include:

- A concise description and impact.
- Affected commit, file or version.
- Reproduction steps or a proof of concept that does not access third-party accounts.
- Suggested mitigation, if known.

## Credential handling

- Never commit provider API keys or broker credentials.
- Use least-privilege keys and disable withdrawal permissions.
- Prefer read-only keys while broker integration is not implemented.
- Use HTTPS for remote provider endpoints.
- Treat local-network HTTP endpoints as trusted only when the user controls that network.
- Do not log authorization headers, secrets or full account responses.

## Trading security boundary

CNCryto's current MVP does not execute orders. Any future broker execution must include explicit user confirmation, strong authentication, order idempotency, server-side or broker-side risk limits, audit logging, secret rotation and sandbox testing before release.
