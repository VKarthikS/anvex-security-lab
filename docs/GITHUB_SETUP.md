# Maintaining the ANVEX GitHub project

Repository: <https://github.com/VKarthikS/anvex-security-lab>

The initial source import is already complete. Keep `main` stable; open a feature branch for each improvement and merge only after GitHub Actions passes.

## One-branch update workflow

```bash
git switch main
git pull --ff-only origin main
git switch -c feat/showcase-integrity
# Copy verified updated files into this working tree.
git status --short
git add README.md docs simulator/src frontend/apps/simulator/src/views/HistoryView.tsx
git commit -m "Improve ANVEX showcase and comparison integrity"
git push -u origin feat/showcase-integrity
```

Open the Pull requests tab on GitHub and merge after all CI checks pass. Do not commit `.env`, `.git`, `node_modules`, runtime databases, `target/`, or logs.

## Release readiness

- [ ] Build and test workflow green on the feature branch
- [ ] Defender and simulator start locally
- [ ] Comparison check: identical runs accepted; mismatched scenario/target/settings produce `incomparable-parameters` and no percentages
- [ ] The newest Mission Control UI exercised with actual backend traffic
- [ ] Genuine screenshot of a completed comparison included in README **only after capture**
- [ ] Contributors correctly credited, and license terms decided
- [ ] Two physical machines tested before describing LAN operation as verified
- [ ] Change repository visibility to Public only after a code/data review
