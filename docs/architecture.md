# Architecture

```text
site-data/peers/*.json -> Hugo templates -> build/site -> GitHub Pages
         ^                                  |
         +---------- Git history -----------+
```

- Git repository permissions control writes.
- Pull requests provide review and an audit trail.
- Hugo validates peer data while rendering the directory.
- Hugo publishes `peers.json` and one configuration per peer.
- GitHub Pages serves only static files.

Generated configurations contain public peer data and a private-key
placeholder. Private keys stay on each user's device.
