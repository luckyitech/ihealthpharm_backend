# Setting the application secrets

As of October 2026 the backend reads its secrets from the environment instead of having them
written into `application.properties`. This file explains how that works on the production server.

Background and the reason for the change: `AUTH-HARDENING-PLAN.md` section 0.

| Variable | Required? | If unset |
|---|---|---|
| `JWT_SIGNING_SECRET` | **Yes** | **The application refuses to start.** |
| `MAIL_PASSWORD` | Recommended | Starts, logs a warning; outgoing mail fails. |
| `DB_PASSWORD` | Recommended | Falls back to `root`, logs a warning. |

`config/StartupSecretsValidator` checks all three at startup and rejects known-weak values —
including the literal `mySecret` that used to be committed to this repository — so a misconfigured
deployment fails loudly instead of quietly running on a key an attacker already has.

---

## 1. READ THIS FIRST — production does not use this repository's properties file

The server runs the war with an **external** config file:

```
java.exe -jar C:\Users\Administrator\iHealth\ihealthpharm_backend\backend\target\Ihealthpharm-0.0.1-SNAPSHOT.war
         --spring.config.location=file:C:/Users/Administrator/iHealth/application.properties
```

`--spring.config.location` **replaces** the war's bundled properties rather than adding to it. So:

- The file in this repository, `backend/src/main/resources/application.properties`, is what **local
  development** and the **build** use. Production ignores it entirely.
- The file that production actually reads is `C:\Users\Administrator\iHealth\application.properties`,
  which is **not in git** and has no history.

**Two things follow, and both matter:**

1. **The old `mySecret` key is almost certainly still live on the server.** Changing it in this
   repository does nothing to production. The rotation has to be done in the external file (or via
   pm2, section 3).
2. **Every property added here must be added there by hand, or the server will not start.** The
   external file is a snapshot from whenever it was created. This repository's copy now defines 488
   properties, including the M-Pesa block and the JWT block; if the external file predates any of
   them, those settings are simply missing on production. Before deploying, diff the two
   (section 5, step 1). This is the single most likely cause of a failed deploy.

---

## 2. Generate the signing key

```bash
openssl rand -base64 64
```

88 characters, 512 bits — the matching length for HS512. Shorter keys start but log a warning.

Store it in a password manager. Losing it logs everyone out (recoverable — they sign in again).
Leaking it is a full authentication bypass (not recoverable without rotating).

Rotating it invalidates every token in circulation, so **everybody is logged out once** on the
deploy that changes it. Expected, and a one-time event.

---

## 3. Where to put the secrets

### Option A — pm2 environment variables (recommended)

The backend runs under pm2 as `ihealthpharm-api`, defined in
`C:\Users\Administrator\iHealth\pm2.config.js`. Add an `env` block to that app's entry:

```js
{
  name: 'ihealthpharm-api',
  // ... existing script / args / kill_timeout: 30000 ...
  env: {
    JWT_SIGNING_SECRET: '<the 88-character value>',
    MAIL_PASSWORD:      '<the new mail password>',
    DB_PASSWORD:        '<the new database password>'
  }
}
```

Keep the external `application.properties` referring to them:

```properties
jwt.signing.key.secret=${JWT_SIGNING_SECRET:}
spring.mail.password=${MAIL_PASSWORD:}
spring.datasource.password=${DB_PASSWORD:root}
```

> **pm2 trap:** `pm2 restart ihealthpharm-api` does **not** pick up a changed `env` block. You must
> use `--update-env`, or delete and re-add the app:
>
> ```
> pm2 restart ihealthpharm-api --update-env
> # or, if that does not take:
> pm2 delete ihealthpharm-api
> pm2 start C:\Users\Administrator\iHealth\pm2.config.js
> ```
>
> Then `pm2 save`, or a later `pm2 resurrect` restores the old settings.

### Option B — straight into the external properties file

```properties
jwt.signing.key.secret=<the 88-character value>
spring.mail.password=<the new mail password>
spring.datasource.password=<the new database password>
```

Simpler, and the file is already outside git, so the original exposure problem is solved either
way. Option A is still preferred: it keeps the values out of a config file that gets copied around
and backed up, and it survives the properties file being replaced on a future deploy.

**Do not do both.** If the properties file has a literal value, it wins and the environment
variable is ignored — which makes a stale secret very hard to spot.

---

## 4. What does NOT need doing

- **Nothing on the frontend.** Angular is static files in `C:\inetpub\wwwroot\ipharmke`, served by
  IIS, which proxies `/ihealthpharmservice/*` to `127.0.0.1:6009`. No restart, no secrets.
- **Nothing with Tomcat.** Tomcat 10.1 is installed but stopped and set to manual, and holds only
  its default apps. It is not involved in running this application.

---

## 5. Deploy sequence

Order matters. The application will refuse to start if the key is missing, so configure first and
deploy second.

**Step 1 — diff the two properties files.** On the server, compare
`C:\Users\Administrator\iHealth\application.properties` against this repository's
`backend/src/main/resources/application.properties`. Anything present here and missing there has to
be added, or the new build will fail on a missing property. Pay particular attention to the JWT
block and anything added since the external file was created.

**Step 2 — set the secrets** (section 3) while the current build is still running. Nothing reads
them yet, so this is inert and safe.

**Step 3 — rotate the actual credentials.** Change the mail password at one.com and the database
password, and update the values from step 2 to match. Until the mail password is rotated, that
mailbox cannot safely carry two-factor codes — its old password is in public git history.

**Step 4 — build and copy the new war.**

```bash
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@11/11.0.32.1/libexec/openjdk.jdk/Contents/Home
cd ihealthpharm_backend/backend && mvn clean package
```

Copy `target/Ihealthpharm-0.0.1-SNAPSHOT.war` to
`C:\Users\Administrator\iHealth\ihealthpharm_backend\backend\target\`. Keep the previous war — it
is the rollback.

**Step 5 — restart.**

```
pm2 restart ihealthpharm-api --update-env
```

It takes about **75 seconds** to start. `pm2 list` showing "online" is not proof on its own.

**Step 6 — verify** (section 6).

---

## 6. Confirm it worked

**Check there is exactly one `java.exe` and that it owns port 6009.** The config allows 30 seconds
for shutdown (`kill_timeout: 30000`) precisely because a restart can otherwise leave an old process
holding the port — in which case you are still running the old build and will not know it.

In the pm2 log (`pm2 logs ihealthpharm-api`):

```
Secret JWT_SIGNING_SECRET accepted.
Secret MAIL_PASSWORD accepted.
Secret DB_PASSWORD accepted.
Started IHealthPharmApplication
```

A refusal names the variable and the reason:

```
JWT_SIGNING_SECRET is not set, so the key alone is enough to forge a session token
for any user, bypassing login and two-factor entirely. Set it in the environment ...
```

Then confirm a real login through IIS:

```bash
curl -s -o /dev/null -w "%{http_code}\n" -X POST \
  https://ipharmke.com/ihealthpharmservice/authenticate \
  -H 'Content-Type: application/json' \
  -d '{"userName":"<user>","password":"<password>"}'
```

A `401` from the base URL when not logged in is normal. Everyone is logged out by the key
rotation, so browsers will ask for credentials again — expected.

**Rollback:** restore the previous war, revert the properties change, `pm2 restart
ihealthpharm-api --update-env`.

---

## 7. After a reboot

The server has not rebooted since April 2026, and the "PM2 Auto Start" scheduled task is set to run
only while Administrator is logged in — which normally prevents a task from firing at boot. So pm2
may not come back on its own.

This does not affect anything above (no reboot is needed to rotate secrets), but if the server ever
restarts, expect to log in and run:

```
pm2 resurrect
```

That also restarts DocPharma and cdc-hms-api. Worth fixing separately so it is not discovered
during an outage.

---

## 8. Local development

`mvn spring-boot:run` now needs the signing key or it will not start. `MAIL_PASSWORD` and
`DB_PASSWORD` can be left unset locally — mail fails and the database falls back to `root`, each
with a warning.

```bash
export JAVA_HOME=/opt/homebrew/Cellar/openjdk@11/11.0.32.1/libexec/openjdk.jdk/Contents/Home
export JWT_SIGNING_SECRET="$(openssl rand -base64 64)"
mvn spring-boot:run
```

Use a throwaway key locally, never the production one. To avoid re-logging-in each time, put the
export in your shell profile — **not** in a file inside the repository.

---

## 9. Do not

- **Do not put any of these back into a tracked properties file.** Both repositories were public
  until October 2026, which is how the previous key leaked.
- **Do not reuse the old values.** `mySecret` and `dpl_req1` are in public git history and are
  compromised permanently. Making the repository private does not un-publish them.
- **Do not set a secret in both the properties file and pm2.** The properties file wins silently.
