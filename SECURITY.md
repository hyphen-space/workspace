# Security policy

## Report a vulnerability

Do not open a public issue for an unpatched vulnerability. Use GitHub's private
vulnerability reporting feature for this repository. If that feature is not
available, contact the repository owner privately.

Include the affected version, reproduction steps, impact, and a proposed fix if
you have one. Do not include real private keys, bearer tokens, or personal data.

## Deployment scope

This project is an early prototype. Peer data is public and stays in SQLite.
Place the service behind HTTPS before you send bearer tokens across a network.
Use a distributed rate limiter when you run multiple instances. Add durable
audit logs, token rotation, and tested backups before an internet-facing
production deployment.
