# Security and responsible use

ANVEX is a **defensive teaching and experiment environment** for its own dummy login application.

- Simulate activity **only** against systems you own or have explicit authorization to test.
- Do not expose the defender or simulator directly to the public internet. The demonstration uses HTTP by default; it is not designed to protect real credentials or user data.
- The simulator checks the remote ANVEX identity/version endpoint and refuses redirects, but a matching handshake is **not authentication** of a remote host. Use trusted networks and explicitly configured addresses.
- Do not load real user accounts, password dumps, third-party targets, or production datasets.
- Analyst controls are restricted by observed loopback source, not by a complete production identity/authorization system.
- Review false positives: source-wide rate limiting/blocks can affect shared NAT users.
- Never commit `.env`, runtime databases, logs, credentials, tokens, or local report exports.

If you discover a security issue in ANVEX, avoid publishing exploit details or private data in public issues. Contact the repository owner through an appropriate private channel or GitHub's private vulnerability reporting feature if enabled.
