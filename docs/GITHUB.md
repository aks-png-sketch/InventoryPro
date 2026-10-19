# Publishing InventoryPro to GitHub

Do this only after deciding to upload. This guide does not create a repository or push anything by itself.

## Work from the local repository

Run the commands below from the local InventoryPro checkout that passes `git status`. Keep generated tools and build output outside Git. Do not initialize Git over an existing `.git` directory.

## Review the first commit

```powershell
cd C:\path\to\InventoryPro
git status --short --branch
git remote -v
git check-ignore -v .env frontend/.env .tools/qa-oracle.log frontend/node_modules backend/target frontend/dist
git add .
git diff --cached --stat
git diff --cached
```

Stage only application source, tests, README, docs, SQL and example environment files. Ensure no password, token, wallet, actual `.env`, `.tools`, `node_modules`, `target`, `dist` or personal data appears in the staged diff. If a file must be removed from staging, use `git restore --staged -- path/to/file` and add an ignore rule. Run `git diff --cached --check` before committing.

## Invite the professor after publication

Open `https://github.com/aks-png-sketch/InventoryPro`, then select **Settings → Collaborators → Add people**. Search for the exact username `SWAMINATHAN-A`, confirm the selected account matches [the supplied profile](https://github.com/SWAMINATHAN-A), and send the invitation. For a repository owned by a personal GitHub account, collaborator access includes **Write**; GitHub does not offer a separate read-only collaborator role for a private personal repository. Access begins after the professor accepts the invitation. See [GitHub's invitation steps](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/repository-access-and-collaboration/inviting-collaborators-to-a-personal-repository) and [personal repository permissions](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/repository-access-and-collaboration/permission-levels-for-a-personal-account-repository).

## Verify the published repository

On GitHub, confirm the root shows `README.md`, `Dockerfile`, `backend/`, `frontend/`, `database/`, `docs/`, and `scripts/`. Inspect `backend/pom.xml` for `ojdbc11`, `backend/src/main/resources/application.yml` for the Thin JDBC URL, `InventoryService.java` for `JdbcTemplate` and stock transactions, and `JdbcDemo.java` for `DriverManager`, prepared statements, commit and rollback. Confirm `database/01_schema.sql` through `05_rollback_demo.sql` and `docs/JDBC.md` are visible. Confirm no real `.env`, wallet, `.tools`, `node_modules`, `target`, or `dist` appears. Source publication does not include Oracle records or passwords.

## Publish when approved

Use the empty GitHub repository named `InventoryPro` under your own account. Confirm its remote URL and visibility, then run only after approval:

```powershell
git commit -m "Build InventoryPro with Oracle JDBC and tested stock transactions"
git branch -M main
git remote add origin https://github.com/aks-png-sketch/InventoryPro.git
git push -u origin main
```

Replace `aks-png-sketch` with your account. Authenticate through GitHub's supported prompt without putting tokens in source or commands. If `git remote -v` already shows the intended `origin`, skip `git remote add origin`. Never force-push for this initial upload. Oracle database records are not included in the source repository; the SQL scripts define schema, seeds and lab examples.
