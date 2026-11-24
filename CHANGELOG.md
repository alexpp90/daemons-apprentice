# Changelog

## 1.4

- **Chapters with difficulty ratings.** Levels are now grouped into six
  chapters rated **Beginner**, **Intermediate**, and **Advanced** in the
  level menu, level banners, status, and `CHEATSHEET.md`. *Glass Rooms &
  Iron Walls* moved next to *Behind the Curtain* (Chapter 4), and older
  v1.2/v1.3 save files migrate automatically.
- **Chapter 6: Real-World Pentesting & Defense (`Advanced`).** Four new
  hands-on Attack -> Fix -> Verify levels (Levels 16–19, 44 tasks, 8 new
  flags) for both Linux and macOS using stock tools only:
  - *Level 16 — Local Recon & Privilege Traps:* SUID discovery, writable
    scheduled script hijacking and hardening, symlink redirection, and
    wildcard flag injection (`cp *` vs `cp -- *`).
  - *Level 17 — Web & HTTP Exploitation:* Local-only (`127.0.0.1`) HTTP
    lab covering response headers, `robots.txt`, exposed `.env`, `../`
    path traversal, IDOR, cookie tampering, live patching, and re-testing.
  - *Level 18 — Terminal & Supply-Chain Traps:* Hidden ANSI escape bytes
    (`cat -v`), Tar Slip archive inspection (`tar -tf`), SSH key locking
    and `authorized_keys` backdoor removal, and `openssl` digital
    signatures.
  - *Level 19 — Network Exposure & TLS Defense:* `0.0.0.0` vs `127.0.0.1`
    binding audit, safe loopback outbound callback demo, TLS certificate
    inspection and verification, `--cacert` pinning vs `curl -k`, and a
    5-question Defender's Checklist quiz.
- 20 levels (0 to 19) with 238 tasks, plus 24 tasks in the two side
  quests, 24 flags, and an extended placement check up to Level 16.

## 1.3

The first public release.

- **Two side quests.** You're offered them along the way, can say no,
  and can always reach them from the level menu.
  - *Portrait of Your Computer* (after Level 6): read-only questions about
    your own machine. The answers and the commands that found them are
    collected into `my-computer.txt`.
  - *The Supervisor* (after Level 11): the game plays an AI agent on a
    mission. You allow, deny or ask why, and whatever you allow really
    runs. A wrong yes shows the damage, and you repair it.
- 16 levels (0 to 15) with 194 tasks, plus 24 tasks in the side quests,
  16 flags and a certificate.
- Every release is tested on Linux and on real Macs (Apple silicon and
  Intel), using the Mac's own Bash 3.2.

## 1.2

- Two new levels before scripting: *The Editor* (nano) and *Building
  Blocks* (commands inside commands, loops).
- What a command changes is shown, not just described. Clearer tasks and
  a gentler climb in the middle levels.

## 1.1

- A new start: Level 0, *Hello, Terminal*, with Pid1 introducing itself.
- Small steps everywhere: one thing per task, and a look before every
  change.
- A clean screen that shows your level, your progress, what you typed and
  what came back.
- `--log` records a whole session as plain text, for playtests.

## 1.0

- The first complete version: Levels 1 to 13, a placement check, typing
  help for US and German keyboards, a cheatsheet that grows with you, and
  separate versions for Mac and Linux.
