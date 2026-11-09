# Publish ANVEX to a fresh GitHub repository

The original `VKarthikS/ANVEX` JavaFX project should remain intact. The recommended new project is **`VKarthikS/anvex-security-lab`**.

1. While signed into GitHub as **VKarthikS**, visit <https://github.com/new>.
2. Repository owner: `VKarthikS`; name: `anvex-security-lab`.
3. Suggested description: `Explainable authentication threat detection and response lab built with Java 21, Spring Boot, and React.`
4. Start **Private** while confirming attribution, demo screenshots, and project permissions; switch to Public for your résumé once checked.
5. Do **not** initialize with README, `.gitignore`, or license — they are already prepared here.
6. Extract the supplied `ANVEX-portfolio-ready.zip`, open a terminal *inside* the `ANVEX-portfolio-ready` folder, and run:

```bash
git init
git add .
git commit -m "Initial import: ANVEX authentication defense lab"
git branch -M main
git remote add origin https://github.com/VKarthikS/anvex-security-lab.git
git push -u origin main
```

If git reports that a user name/email is missing, set those values to your intended public commit attribution. GitHub will request authentication; use GitHub Credential Manager or the supported browser login flow, **never** paste tokens into chat or commit them.

Before changing the repository to Public, review the files on GitHub, run Actions CI, confirm the screenshots depict the current release accurately, and add appropriate contributor credit and a license only when you've chosen terms.

**Suggested topics:** `cybersecurity`, `blue-team`, `java`, `spring-boot`, `react`, `authentication`, `intrusion-detection`, `security-lab`, `incident-response`
