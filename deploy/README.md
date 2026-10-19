# Hosting the group's hub — runbook

The hub is the group's own server (ADR-030): Android phones sync with it on their own, and
iPhones (or any browser) use the whole app as a web app. It runs on a spare machine at home. It
is free, and friends install nothing.

**The usual way: Tailscale Funnel.** Tailscale (which you already use) publishes the hub at a
public HTTPS address like `https://goa.cat-crocodile.ts.net`. No router change, no open port, no
domain to buy. Only the hub machine runs Tailscale; friends just open the link.

**The alternative, without Tailscale:** Caddy + a free DuckDNS name + a router port forward. See
[Appendix A](#appendix-a-without-tailscale-caddy--duckdns--router).

> **Status (2026-10-19):** these scripts were tested in Debian 13, Ubuntu 24.04 and Fedora 44
> containers against a stand-in hub, but **not yet on a real machine with a real tailnet**. If
> a step doesn't match what you see, note it down; it gets fixed in this file.

Contents

1. [How it fits together](#1-how-it-fits-together)
2. [What you need](#2-what-you-need)
3. [Prepare your tailnet (once)](#3-prepare-your-tailnet-once)
4. [Build on your PC](#4-build-on-your-pc)
5. [Install on Linux](#5-install-on-linux)
6. [Invite the group](#6-invite-the-group)
7. [Updating](#7-updating)
8. [Backups](#8-backups)
9. [Troubleshooting](#9-troubleshooting)
10. [Security notes](#10-security-notes)
11. [Windows instead of Linux](#11-windows-instead-of-linux)
12. [Appendix A: without Tailscale (Caddy + DuckDNS + router)](#appendix-a-without-tailscale-caddy--duckdns--router)

---

## 1. How it fits together

```
iPhone / Android / browser
        │  https://goa.<tailnet>.ts.net
        ▼
Tailscale Funnel relay (public; passes encrypted bytes, can't read them)
        │
        ▼  the hub machine, at home
tailscaled  ── HTTPS ends here (the certificate lives on this machine)
        │  http://127.0.0.1:8080  (this machine only)
        ▼
goa-hub  (Java; data in /var/lib/goa-hub)
```

- The hub listens on `127.0.0.1:8080` only. Nothing on your network or the internet can reach
  port 8080 directly.
- Funnel connects **out** from the machine to Tailscale, so the router needs no changes and no
  inbound port is opened. It also works behind carrier-grade NAT.
- Anyone on the internet can *reach* the address. What protects the group's data is the hub's
  own sign-in: a browser only gets in after scanning the group's invite QR code.

## 2. What you need

- **A spare machine** that stays on, on a wired connection if possible. Linux is easiest
  (Debian 13, Ubuntu 24.04 or 26.04; Fedora 43+ works too). Windows 10/11 also works
  ([section 11](#11-windows-instead-of-linux)). Any 64-bit machine from the last ten years with
  2 GB of RAM is plenty.
- **Your Tailscale account** and admin access to your tailnet's admin console
  (<https://login.tailscale.com/admin>). The free Personal plan is enough: Funnel is available on
  all plans. Tailscale labels Funnel "beta".
- **Your Windows PC** with this repository, able to build the app (see the main
  [README](../README.md)).
- **An Android phone that is in the group** (for the invite).
- About 30–45 minutes.

## 3. Prepare your tailnet (once)

All in the admin console, <https://login.tailscale.com/admin>.

**3.1 Turn on MagicDNS and HTTPS.** Open the **DNS** page.
- Under MagicDNS: **Enable MagicDNS** (skip if it is on already).
- Under HTTPS Certificates: **Enable HTTPS**, and accept the notice.

The notice matters: your machine names and your tailnet name get published in the public
Certificate Transparency logs, as every HTTPS certificate is. So don't use personal details in
machine names. It also means people *will* find the hub's address. That's fine: the hub only lets
in browsers that scanned the group's invite.

**3.2 Your tailnet name (and a nicer one).** The DNS page shows your tailnet DNS name, something
like `tail1a2b3c.ts.net`. The hub's address will be `https://goa.<that name>`.

If you'd like a friendlier address, do this **now, before anyone uses the hub**: on the DNS page,
**Rename tailnet…**, pick one of the offered "fun names" (for example `cat-crocodile.ts.net`;
**Re-roll options** shows others) and confirm. Changing it later breaks every link and invite
you've handed out, and a fun name can't be swapped for a different fun name once it has been used
for HTTPS certificates (you can always go back to the original `tail…` name).

**3.3 Allow Funnel for the hub machine only.** Open **Access controls** and edit the policy file.
Add a tag for the hub and let only that tag use Funnel. Merge these two sections into the
policy (if `tagOwners` or `nodeAttrs` already exist, add the entries to them):

```jsonc
"tagOwners": {
	"tag:goa-hub": ["autogroup:admin"],
},
"nodeAttrs": [
	{
		"target": ["tag:goa-hub"],
		"attr":   ["funnel"],
	},
],
```

Save. The hub machine gets the tag when it logs in (step 5.3). A tagged machine also has **key
expiry turned off**, so the hub doesn't drop off the tailnet after 180 days.

> If you skip 3.3, the installer's Funnel step prints a link instead; opening it and approving
> lets every member's devices use Funnel. That works too, but then also turn off key expiry for
> the hub machine (Machines → the machine → ⋯ → **Disable key expiry**).

## 4. Build on your PC

In PowerShell, in the repository folder (C: is nearly full, so set the build folders first):

```powershell
$env:GRADLE_USER_HOME = "D:\.gradle-home"; $env:TMP = "D:\.tmp-build"; $env:TEMP = "D:\.tmp-build"
.\gradlew.bat :server:hub:distZip
# -> server\hub\build\distributions\goa-hub-<version>.zip
.\gradlew.bat :app:assembleRelease
# -> app\build\outputs\apk\release\app-release.apk
```

**Optional: the release key's fingerprint.** With it, invite links open the Android app directly
on phones that have it. Android Studio ships `keytool`; the store password is in
`D:\.goa-signing\keystore.properties`:

```powershell
& "C:\Program Files\Android\Android Studio\jbr\bin\keytool.exe" -list -v -keystore D:\.goa-signing\goa-release.jks
```

Copy the value after `SHA256:` (looks like `AA:BB:CC:…`, 32 pairs).

## 5. Install on Linux

**5.1 The machine.** Install Debian 13 or Ubuntu Server (24.04 or 26.04). During setup, create
your own user and tick **OpenSSH server** so you can work from your PC. Then, on the machine:

```bash
sudo apt update && sudo apt full-upgrade -y
```

Fedora: `sudo dnf upgrade -y` instead. Notes for Fedora are marked *Fedora* below.

**5.2 Copy the files over.** From PowerShell on your PC (Windows has `scp` built in). Replace
`you` with your user on the machine and `192.168.1.50` with its address (shown on its screen
after login with `ip -4 addr`, or in your router's device list):

```powershell
scp -r .\deploy .\server\hub\build\distributions\goa-hub-1.0.0.zip .\app\build\outputs\apk\release\app-release.apk you@192.168.1.50:~/
```

No network yet? A USB stick works too: copy the `deploy` folder and the two files onto it, then
on the machine copy them into your home folder.

**5.3 Log the machine in to Tailscale.** On the machine (over SSH: `ssh you@192.168.1.50`):

```bash
curl -fsSL https://tailscale.com/install.sh | sh
sudo tailscale up --hostname=goa --advertise-tags=tag:goa-hub
```

The second command prints a link. Open it on your PC, sign in, and approve the machine. Check:

```bash
tailscale status
```

The first line shows `goa` with `tagged-devices`. From now on, connect from your PC through the
tailnet: `ssh you@goa`.

- *The link says the tag is not allowed:* step 3.3 isn't saved yet, or your account isn't an
  admin of the tailnet.
- *Another machine is already called `goa`:* Tailscale names this one `goa-1`. Rename it in the
  admin console (Machines → ⋯ → Edit machine name) or pick another `--hostname`.

**5.4 Get the group invite.** The hub joins the group like a phone does. On an Android phone in
the group: **Settings → Group sync → Invite a friend → Share as text**, and send it to yourself
(an email or a note you can open on the PC). You need the **last line** of that message: it starts
with `goa-sync:1?` or `https://`. It is the group's key, so delete the message afterwards.

The hub has **no web page for joining**: joining is only done here, on the machine.

**5.5 Run the installer.**

```bash
sudo bash ~/deploy/linux/install.sh ~/goa-hub-1.0.0.zip
```

It asks for the invite: paste the line from 5.4 and press Enter. It also asks which **time
zone** the hub shows dates in (match days, "today", backup names), offering the machine's own: press
Enter to keep it, or type an IANA name such as `Europe/Amsterdam` (`timedatectl list-timezones`
lists them). With the fingerprint from step 4, start it like this instead:

```bash
sudo GOA_ANDROID_CERT_SHA256=AA:BB:…:FF bash ~/deploy/linux/install.sh ~/goa-hub-1.0.0.zip
```

What it does, in order (it is safe to run again; it only fills in what is missing):

1. Installs Java 21 (*Fedora 44+*: Java 25, which runs the hub just the same), `curl` and `unzip`.
2. Creates the `goa-hub` system user, `/opt/goa-hub` (the program), `/var/lib/goa-hub` (the data,
   only `goa-hub` can read it), `/var/backups/goa-hub` and `/etc/goa-hub` (root only).
3. Installs the `goa-hub` systemd service and the daily backup timer, and keeps a copy of this
   kit in `/opt/goa-hub/deploy`.
4. Unpacks the hub, starts it on `127.0.0.1:8080` and checks it answers.
5. Joins the group with your invite (`goa-hub join`) and sets the public address and time zone
   (`goa-hub config --public-url https://goa.<tailnet>.ts.net --time-zone Europe/Amsterdam`).
6. Publishes the hub: `tailscale funnel --bg --https=443 http://127.0.0.1:8080`. `--bg` means
   Tailscale keeps this setting and restores it after every restart and reboot.
7. Turns on automatic security updates (`unattended-upgrades`; *Fedora*: `dnf5-automatic`) and
   Tailscale's own auto-update.

It ends by printing the hub's address. Check on the machine:

```bash
systemctl status goa-hub            # "active (running)"
curl http://127.0.0.1:8080/api/v1/health   # {"status":"ok"}
tailscale funnel status             # the address, "(Funnel on)", and "proxy http://127.0.0.1:8080"
sudo -u goa-hub /opt/goa-hub/current/bin/goa-hub status --data /var/lib/goa-hub
```

**5.6 Check from outside.** On your phone, **switch Tailscale off** (otherwise the phone takes the
private tailnet route and you aren't testing the public one), turn Wi-Fi off, and open:

```
https://goa.<tailnet>.ts.net/api/v1/health
```

You should see `{"status":"ok"}` with a padlock and no warning. A brand-new address can take up to
10 minutes to appear the first time.

**5.7 Publish the Android app** (so friends can get it from the hub instead of the Drive link):

```bash
sudo /opt/goa-hub/deploy/linux/update.sh ~/app-release.apk
```

Signed-in browsers can then download it at `https://goa.<tailnet>.ts.net/app`.

## 6. Invite the group

1. **Your Android phone first:** **Settings → Group sync → Hub**, enter
   `https://goa.<tailnet>.ts.net`, then **Sync now**. The Group screen shows "Hub: synced just now".
   Phones that join with a hub invite get the address by themselves.
2. **At the table**, on that phone: **Group sync → Invite a friend**. With a hub set, the QR code
   is a link to the hub.
3. **iPhone friends:** open the **Camera**, point it at the QR code, tap the yellow link. Safari
   opens the hub and signs them in. Then, in Safari: **Share → Add to Home Screen → Add**, and from
   now on open the app from that icon.
4. **Timer alerts on a locked iPhone:** in the home-screen app, open the Timer and tap the button
   that allows notifications, then **Allow**. This needs iOS 16.4 or later and only works from the
   home-screen icon, not from a Safari tab.
5. **Android friends** without the app: once signed in on the web, they download it from
   `https://goa.<tailnet>.ts.net/app`, install it, and scan the invite in the app (Settings → Group
   sync → Scan invite QR code). The app takes the hub address from the invite.

The invite QR code is the group's key: show it only at the table, and never post the hub's
address anywhere public (ADR-006).

## 7. Updating

**A new hub version.** Build the zip on the PC (step 4), copy it over and run `update.sh`:

```powershell
scp .\server\hub\build\distributions\goa-hub-1.1.0.zip you@goa:~/
ssh you@goa
```
```bash
sudo /opt/goa-hub/deploy/linux/update.sh ~/goa-hub-1.1.0.zip
```

It unpacks the new version next to the old one, stops the hub, backs up the data
(`/var/backups/goa-hub/goa-hub-before-update-<date>.tar.gz`), switches `/opt/goa-hub/current`
to the new version, starts it and checks `/api/v1/health`. If the new version doesn't answer
within 90 seconds (or crashes three times), it **rolls back** by itself: the old version and the
data from just before come back, and it tells you where the failed attempt's log and data are.
The last three versions stay in `/opt/goa-hub/releases`.

**A new Android app.** Bump `versionCode` (see the main README), build, copy, then:

```bash
sudo /opt/goa-hub/deploy/linux/update.sh ~/app-release.apk
```

**Update the hub and the app together** when the sync format changes: a phone and a hub on
different sync versions refuse to sync ("409") until both are updated.

**A newer copy of this kit** (the `deploy` folder changed): copy the folder over again and run the
installer without a zip. `GOA_HUB_INVITE=` (empty) skips the invite question:

```bash
sudo GOA_HUB_INVITE= bash ~/deploy/linux/install.sh
```

**Another time zone** (the group moved, or the machine's clock zone is UTC): `goa-hub config`
changes only the options you give it, and the running hub picks the new zone up at once.

```bash
sudo -u goa-hub /opt/goa-hub/current/bin/goa-hub config --data /var/lib/goa-hub --time-zone Europe/Amsterdam
```

`goa-hub status` shows the zone in use; without one set, it is the machine's
(`timedatectl`). Only IANA names are accepted (`Europe/Amsterdam`, not `CET` or `+02:00`, which
would miss the clock change).

## 8. Backups

**What matters:** `/var/lib/goa-hub`. It holds the group key, the hub's copy of the group's data
(plus a dated copy per day for 30 days), each browser's sign-in and settings, the Web Push keys
and the published APK.

Good to know: every phone in the group also holds the full match log. If the hub machine dies,
you can set up a new one, join it with a fresh invite, and the phones fill it up again on their
next sync. What a backup saves on top is the iPhones' sign-ins and settings (otherwise they scan
the invite again) and their notification permissions.

**Automatic:** every day around 04:00 the `goa-hub-backup` timer writes
`/var/backups/goa-hub/goa-hub-<date>.tar.gz` and deletes copies older than 14 days.

```bash
sudo ls -lh /var/backups/goa-hub                 # the backups
systemctl list-timers goa-hub-backup             # when the next one runs
sudo systemctl start goa-hub-backup              # make one now
journalctl -u goa-hub-backup -n 5                # did it work?
```

**Keep a copy off the machine** (a disk failure takes the backups with it). Once in a while, on
the machine:

```bash
latest="$(sudo ls -1t /var/backups/goa-hub/goa-hub-*.tar.gz | head -n 1)"
sudo install -m 600 -o "$USER" "$latest" ~/goa-hub-backup.tar.gz
```

then from PowerShell on the PC, and remove the copy on the machine:

```powershell
New-Item -ItemType Directory -Force D:\goa-hub-backups | Out-Null
scp you@goa:~/goa-hub-backup.tar.gz "D:\goa-hub-backups\goa-hub-$(Get-Date -Format yyyyMMdd).tar.gz"
ssh you@goa "rm ~/goa-hub-backup.tar.gz"
```

Keep it somewhere private: it contains the group key.

**Restore** (for example after reinstalling the machine, run the installer first, then):

```bash
sudo systemctl stop goa-hub
sudo mv /var/lib/goa-hub /var/lib/goa-hub.old
sudo tar -xzf /var/backups/goa-hub/goa-hub-20261101-040312.tar.gz -C /var/lib
sudo chown -R goa-hub:goa-hub /var/lib/goa-hub && sudo chmod 700 /var/lib/goa-hub
sudo systemctl start goa-hub
curl http://127.0.0.1:8080/api/v1/health
```

When it works, delete `/var/lib/goa-hub.old`. Tailscale's own state isn't in the backup: on a
reinstalled machine, log in again (step 5.3) and run the installer.

## 9. Troubleshooting

**Where to look first**

```bash
systemctl status goa-hub                  # running?
journalctl -u goa-hub -n 100              # the hub's log (-f to follow it live)
curl http://127.0.0.1:8080/api/v1/health  # does the hub answer on the machine?
tailscale status                          # is the machine on the tailnet?
tailscale funnel status                   # is Funnel on, pointing at 127.0.0.1:8080?
journalctl -u tailscaled -n 50            # Tailscale's log
```

**The address doesn't open from outside.**
1. Testing from your own phone? Switch Tailscale off on it, and use mobile data.
2. `tailscale funnel status` must say `(Funnel on)`. If not:
   `sudo tailscale funnel --bg --https=443 http://127.0.0.1:8080`.
3. A brand-new address can take up to 10 minutes to resolve.
4. The health check on the machine fails? Then it's the hub, not Funnel: read its log.

**"Funnel not available; HTTPS must be enabled"** — step 3.1. **"funnel node attribute not
set"** — step 3.3, and check the machine has the tag (Machines page).

**The hub stopped being reachable after a few months.** The machine's Tailscale key expired. A
machine tagged in step 5.3 doesn't expire; otherwise: Machines → the machine → ⋯ → **Disable key
expiry**, then `sudo tailscale up` on the machine.

**Certificate warning in the browser.** Tailscale gets and renews the certificate on the machine.
Check the machine's clock (below), then `journalctl -u tailscaled | grep -i cert`.

**Clock is wrong (timers jump, sign-ins fail, certificate errors).**

```bash
timedatectl                     # "System clock synchronized: yes" is what you want
sudo timedatectl set-ntp true   # turn on time sync
```

**Phones say "Hub: 409" / "update the app".** Hub and app are on different sync versions: update
both (section 7). **"401"**: the hub is in another group (or none): join it again with a fresh
invite: `sudo systemctl stop goa-hub`, then
`sudo -u goa-hub /opt/goa-hub/current/bin/goa-hub join --data /var/lib/goa-hub` and paste the invite
when it asks (on standard input: no quoting, and the key stays out of the shell history and the
process list), then `sudo systemctl start goa-hub`. On Windows (PowerShell as Administrator):
`Stop-ScheduledTask 'GoA Hub'`, then `C:\goa-hub\current\bin\goa-hub.bat join --data C:\ProgramData\goa-hub`
and paste the invite, then `Start-ScheduledTask 'GoA Hub'` (never as an argument there: `cmd.exe`
splits it at every `&`).
**"413"**: a sync over 8 MB, which shouldn't happen; tell the developer.

**The hub crashes with `OutOfMemoryError`.** Raise `-Xmx384m` in
`/etc/systemd/system/goa-hub.service` (for example to `-Xmx768m`), then
`sudo systemctl daemon-reload && sudo systemctl restart goa-hub`.

**An update went wrong and rolled back.** The output says where the failed version's data is
(`/var/lib/goa-hub.failed-<date>`); the log is above it. The hub is back on the old version.

**Funnel speed.** Funnel has bandwidth limits that Tailscale doesn't publish. Syncs are small
(kilobytes to a few MB), so this is fine for a group; the first look through the hero catalog on a
new iPhone is the heaviest moment.

*Fedora:* if the hub won't start and the log is empty, look for SELinux denials:
`sudo ausearch -m avc -ts recent`; `sudo restorecon -R /opt/goa-hub /var/lib/goa-hub` usually
fixes labels.

## 10. Security notes

- **Nothing listens to the outside.** The hub binds `127.0.0.1:8080`; Funnel connects out. Never
  open or forward port 8080, and never Funnel any other local port.
- **Encryption ends on your machine.** The certificate and its key live on the hub machine;
  Tailscale's Funnel relays forward encrypted traffic they can't read.
- **The address is not a secret.** The `ts.net` name is in public certificate logs, so expect
  bots to knock. The hub's sign-in (invite QR, device cookie, rate-limited joins; ADR-030 §7) is
  the protection. Never put the hub behind anything that skips it.
- **Joining is command-line only.** The hub has no web page for first-time setup.
- **A dedicated user with a locked-down service.** `goa-hub` has no login shell and owns only
  `/var/lib/goa-hub` (mode 0700, files 0600). The systemd unit adds `ProtectSystem=strict` (the
  whole system read-only for the hub), `ReadWritePaths=/var/lib/goa-hub`, `NoNewPrivileges`,
  `PrivateTmp`, `PrivateDevices`, `ProtectHome`, `ProtectKernel*`, an empty capability set,
  `RestrictAddressFamilies`, `SystemCallFilter=@system-service` and more
  (`systemd-analyze security goa-hub` rates it about 1.5, "OK"). `MemoryDenyWriteExecute` is left
  off on purpose: Java's JIT compiler needs it.
- **Automatic updates.** Security updates install by themselves (`unattended-upgrades`;
  *Fedora*: `dnf5-automatic`); Java comes from the distribution, so it's covered. Tailscale updates
  itself. Some updates (the kernel) only take effect after a reboot: reboot once a month, or let
  Debian/Ubuntu do it at night:
  `echo 'Unattended-Upgrade::Automatic-Reboot "true"; Unattended-Upgrade::Automatic-Reboot-Time "05:00";' | sudo tee /etc/apt/apt.conf.d/52goa-hub-reboot`.
  The hub, Funnel and the timers all come back after a reboot.
- **SSH over the tailnet only, with keys.** First copy your key from the PC (once), then test
  that `ssh you@goa` logs in without a password:

  ```powershell
  ssh-keygen -t ed25519      # only if you don't have a key yet; Enter for the defaults
  type $env:USERPROFILE\.ssh\id_ed25519.pub | ssh you@goa "mkdir -p ~/.ssh && cat >> ~/.ssh/authorized_keys"
  ```

  Then, on the machine, **while connected through `ssh you@goa`**:

  ```bash
  printf 'PasswordAuthentication no\nKbdInteractiveAuthentication no\n' | sudo tee /etc/ssh/sshd_config.d/10-goa-hub.conf
  sudo systemctl reload ssh            # Fedora: sudo systemctl reload sshd
  sudo ufw allow in on tailscale0      # everything from your tailnet is allowed …
  sudo ufw default deny incoming       # … everything else is refused
  sudo ufw enable
  ```

  *Fedora (firewalld):* `sudo firewall-cmd --permanent --zone=trusted --add-interface=tailscale0`,
  then `sudo firewall-cmd --permanent --remove-service=ssh` and `sudo firewall-cmd --reload`.
  Funnel keeps working with all inbound ports closed.
- **Whoever has the machine has the data.** The hub holds the group key (ADR-030). Keep the
  machine at home, and consider full-disk encryption when you install the OS.

## 11. Windows instead of Linux

The same hub runs on Windows 10/11. Everything goes in `C:\goa-hub`; the data in
`C:\ProgramData\goa-hub` (SYSTEM and Administrators only). The hub runs as a scheduled task at
startup, so nobody needs to be signed in.

1. Do sections 3 and 4. Copy the `deploy` folder and the zip to the hub PC (or build there).
2. Tailscale: if it isn't on that PC yet, install it from <https://tailscale.com/download/windows>
   (or `winget install Tailscale.Tailscale`) and log in from its tray icon. Tag the PC
   `tag:goa-hub` in the admin console (Machines → ⋯ → **Edit ACL tags**) and **Disable key
   expiry** for it. Its tailnet name is the PC's name; rename it there to `goa` if you like.
3. Open **PowerShell as Administrator** in the `deploy\windows` folder:

   ```powershell
   Set-ExecutionPolicy -Scope Process Bypass
   .\install.ps1 -Zip C:\Users\you\Downloads\goa-hub-1.0.0.zip
   ```

   Add `-AndroidCertSha256 AA:BB:…` for the app links (step 4). It asks for the invite (step 5.4).
   The hub shows dates in the PC's time zone; to pick another, run
   `C:\goa-hub\current\bin\goa-hub.bat config --data C:\ProgramData\goa-hub --time-zone Europe/Amsterdam`
   in PowerShell as Administrator (the running hub picks it up at once).
   It installs Java 21 with winget (Microsoft Build of OpenJDK) if needed, sets up the folders,
   the "GoA Hub" and "GoA Hub backup" scheduled tasks, joins, sets the address, turns on
   Tailscale's unattended mode (keeps running when nobody is signed in) and runs
   `tailscale funnel --bg --https=443 http://127.0.0.1:8080`. It also turns off sleep and
   hibernation on mains power.
4. Check: `http://127.0.0.1:8080/api/v1/health` in a browser on that PC, then section 5.6 from a
   phone.

**Updates** (PowerShell as Administrator, in `C:\goa-hub\deploy\windows`):
`.\update.ps1 goa-hub-1.1.0.zip` (with the same rollback as on Linux) and
`.\update.ps1 app-release.apk`.
**Log:** `C:\goa-hub\logs\hub.log`. **Backups:** `C:\goa-hub\backups\goa-hub-<date>.zip`, daily,
14 days. To restore: stop the task (`Stop-ScheduledTask 'GoA Hub'`), rename
`C:\ProgramData\goa-hub`, unzip the backup into a new `C:\ProgramData\goa-hub`, start it
(`Start-ScheduledTask 'GoA Hub'`). **Firewall:** nothing to open on the Tailscale path.
**Windows Update** restarts are fine: the task, Tailscale and Funnel all start again by
themselves.

## Appendix A: without Tailscale (Caddy + DuckDNS + router)

Use this only if you don't want Tailscale on the hub machine. It needs more: a free DuckDNS name,
Caddy for HTTPS, and two ports forwarded on your router.

**Before you start**

- **Carrier-grade NAT check.** Your router must have a real public IPv4 address. Compare the WAN /
  Internet address on the router's status page with what the machine sees:
  `curl https://api.ipify.org`. They must be the same. If the router's address starts with `100.64`
  to `100.127`, `10.`, `172.16`–`172.31` or `192.168.`, you are behind carrier-grade NAT and this
  appendix will not work: use Tailscale (sections 3–5).
- **Router admin access** (the login is often on a sticker on the router).
- **A fixed LAN address for the machine:** in the router, find "DHCP reservation" / "static lease"
  and reserve the machine's current address.

**DuckDNS.** Sign in at <https://www.duckdns.org> (with Google, GitHub, …), type a name (for
example `ourgroup`) and **add domain**. Copy the **token** at the top of the page. The token
controls the name: keep it private.

**Install.** Steps 5.1, 5.2, 5.4 as above (skip 5.3), then:

```bash
sudo bash ~/deploy/linux/install.sh --with-caddy ~/goa-hub-1.0.0.zip
```

It asks for the DuckDNS name and token too. On top of the usual steps it:
- writes the token to `/etc/goa-hub/duckdns.env` (root only) and starts the `duckdns-update`
  timer, which tells DuckDNS your current home address every 5 minutes
  (`deploy/linux/alt-caddy/duckdns-update.sh`);
- installs Caddy from its official repository (*Fedora*: its COPR), writes `/etc/caddy/Caddyfile`
  from `deploy/linux/alt-caddy/Caddyfile` (HTTPS for `ourgroup.duckdns.org` → `127.0.0.1:8080`,
  HSTS, a 10 MB request limit, an access log in `/var/log/caddy` without query strings or
  cookies) and starts it;
- opens TCP 80 and 443 in ufw / firewalld if one is active.

**Router.** Forward **TCP 443** and **TCP 80** to the machine's fixed address (same port numbers
inside and out). Port 443 carries the hub. Port 80 lets Caddy prove the name is yours to Let's
Encrypt, and redirects `http://` visitors to `https://`. Caddy can also get certificates through
443 alone, so 80 is a strong recommendation rather than a must.

> *DNS challenge, only port 443:* a Caddy build with the DuckDNS plugin can prove ownership
> through DuckDNS instead of port 80. It means downloading a custom Caddy from
> <https://caddyserver.com/download> (add `caddy-dns/duckdns`) that `apt` no longer updates for you,
> so it's not recommended here. If you do: install it next to the packaged one with
> `dpkg-divert` (<https://caddyserver.com/docs/build#package-support-files-for-custom-builds-for-debianubunturaspbian>)
> and add `tls { dns duckdns {env.DUCKDNS_TOKEN} }` to the site block.

**Check from outside:** phone on mobile data (not your home Wi-Fi: many routers can't loop back
to their own public address), open `https://ourgroup.duckdns.org/api/v1/health`. Then configure
phones as in section 6 with `https://ourgroup.duckdns.org`.

**Let's Encrypt limits worth knowing.** `duckdns.org` counts as a public suffix, so your name has
its own limits: 50 certificates a week, 5 identical ones a week, and 5 failed attempts an hour.
Caddy renews by itself and backs off on failures (and can fall back to ZeroSSL). Don't delete
`/var/lib/caddy` over and over while testing.

**Troubleshooting**
- *Certificate errors / no padlock:* `journalctl -u caddy -n 100`. "Timeout during connect" means
  ports 80/443 don't reach the machine: check the router forward and the firewall
  (`sudo ufw status`). A wrong address: check DuckDNS (below).
- *DuckDNS not updating:* `systemctl status duckdns-update` and
  `journalctl -u duckdns-update -n 20`. `KO` means a wrong name or token: fix
  `/etc/goa-hub/duckdns.env`, then `sudo systemctl start duckdns-update`. Compare
  `getent hosts ourgroup.duckdns.org` with `curl https://api.ipify.org`.
- *Works at home but not outside:* the router forward or carrier-grade NAT (see the check above).
- *Fedora, "502 Bad Gateway":* SELinux may block Caddy from reaching the hub:
  `sudo setsebool -P httpd_can_network_connect 1`.

**Windows:** `.\install.ps1 -Zip … -WithCaddy -Domain ourgroup -DuckDnsToken …` downloads the
official Caddy (checksum checked) into `C:\goa-hub\caddy`, runs it as the `goa-caddy` Windows
service, adds a "GoA DuckDNS" task every 5 minutes (`deploy\windows\alt-caddy\duckdns-update.ps1`,
result in `C:\goa-hub\logs\duckdns.log`) and opens TCP 80 and 443 for Caddy in Windows Firewall.
Then forward the ports on the router as above.
