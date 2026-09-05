# Lockbox

[![CI](https://img.shields.io/github/actions/workflow/status/WellNotWell/lockbox/ci.yml?branch=main&label=CI&logo=githubactions&logoColor=white&labelColor=311B92)](https://github.com/WellNotWell/lockbox/actions/workflows/ci.yml)
[![Release](https://img.shields.io/badge/release-v1.0.0-8A2BE2)](https://github.com/WellNotWell/lockbox/releases/latest)
![Java](https://img.shields.io/badge/Java-21-6A1B9A?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-4A148C?logo=springboot&logoColor=white)
![Vaadin](https://img.shields.io/badge/Vaadin-25-6C5CE7?logo=vaadin&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-311B92?logo=postgresql&logoColor=white)
[![License](https://img.shields.io/badge/license-MIT-388E3C)](LICENSE)

A self-hosted vault for passwords, documents and notes. Field values and file contents are
encrypted with AES-256-GCM under per-entry keys, and those keys are wrapped by a key derived from
the master password at sign-in, which lives only in session memory – the database and the object
storage hold nothing but ciphertext.

Vaadin web interface, PostgreSQL for data, S3-compatible MinIO for files. One `docker compose up`
brings the whole thing up.

## Contents

- [Features](#features)
- [Quick start](#quick-start)
- [Configuration](#configuration)
- [Releases](#releases)
- [Architecture](#architecture)
- [Development](#development)
- [Tests](#tests)
- [Project layout](#project-layout)
- [License](#license)

## Features

- **Entries with arbitrary fields** – "Host", "Login", "Password"; every value is encrypted
  separately under the entry key.
- **Secret fields** – the lock keeps a value permanently masked, while the eye inside the field
  reveals it once without changing that property.
- **Files as entry fields** – up to 50 MB, with previews for images, PDF, `.txt` and `.csv`
  right in the entry dialog. They are encrypted as a stream, in 1 MiB chunks, and stored
  in MinIO under a random name.
- **Search** across entry titles, field labels and file names – that is, across what is stored
  in the clear anyway.
- **Master password change** without re-encrypting the data: only the entry keys are rewrapped.
  The five most recent passwords are remembered and rejected on reuse.
- **Encrypted backup** – a zip of metadata and files under a password of its own; the data is
  never decrypted on the way out, only the entry keys are rewrapped.
- **Two languages** (English and Russian) and a dark theme that follows the operating system.

## Quick start

Docker is the only requirement. The image is published to the GitHub Container Registry, so
there is nothing to build:

```bash
curl -O https://raw.githubusercontent.com/WellNotWell/lockbox/main/docker-compose.release.yml
curl -o .env https://raw.githubusercontent.com/WellNotWell/lockbox/main/.env.example
docker compose -f docker-compose.release.yml up -d
```

`latest` is used by default; `LOCKBOX_VERSION` pins a specific one:

```bash
LOCKBOX_VERSION=1.0.0 docker compose -f docker-compose.release.yml up -d
```

Building from source – when you want to change the code:

```bash
git clone https://github.com/WellNotWell/lockbox.git
cd lockbox
cp .env.example .env
docker compose up --build
```

| What          | Where                                 |
|---------------|---------------------------------------|
| Application   | http://localhost:8080                 |
| MinIO console | http://localhost:9001                 |
| Health        | http://localhost:8080/actuator/health |

Then register at `/register`. The master password cannot be recovered: the encryption key is
derived from it, and without it the data is unreadable to everyone, owner included.

## Configuration

Every setting comes from an environment variable, and the defaults are meant for a local run.
The `.env` file only carries the credentials of the PostgreSQL and MinIO containers – without it
everything runs on the defaults.

| Variable                                    | Default                                    | Purpose                            |
|---------------------------------------------|--------------------------------------------|------------------------------------|
| `SERVER_PORT`                               | `8080`                                     | Application port                   |
| `DB_URL`                                    | `jdbc:postgresql://localhost:5432/lockbox` | PostgreSQL address                 |
| `DB_USER` / `DB_PASSWORD`                   | `lockbox` / `lockbox`                      | Database credentials               |
| `STORAGE_ENDPOINT`                          | `http://localhost:9000`                    | S3-compatible storage address      |
| `STORAGE_ACCESS_KEY` / `STORAGE_SECRET_KEY` | `lockbox` / `lockbox-secret`               | Storage credentials                |
| `STORAGE_BUCKET`                            | `lockbox`                                  | Bucket for files                   |
| `CRYPTO_MEMORY_KB`                          | `65536`                                    | Argon2id memory per key derivation |
| `CRYPTO_ITERATIONS`                         | `3`                                        | Argon2id passes                    |
| `CRYPTO_PARALLELISM`                        | `1`                                        | Argon2id parallelism               |

Limits baked into `application.yml`: 50 MB per attachment and 6 hours for an unfinished upload.

### About the credentials in `.env`

The default passwords are safe for exactly one reason: the PostgreSQL and MinIO ports are
published on `127.0.0.1` and unreachable from outside the machine. The only port facing the
network is the application on `8080`, and it asks for the master password.

Change them whenever that stops being true – the application moves to a server, or the database
and storage ports are opened up. `DB_PASSWORD` is the PostgreSQL password; `STORAGE_ACCESS_KEY`
and `STORAGE_SECRET_KEY` are the MinIO root account, that is, full access to the bucket. They
have nothing to do with the encryption: holding them gets an attacker ciphertext rather than
data – but it does let them delete everything.

Generate random ones into `.env` (this replaces the file if it already exists):

```bash
printf 'DB_PASSWORD=%s\nSTORAGE_ACCESS_KEY=lockbox\nSTORAGE_SECRET_KEY=%s\n' \
  "$(openssl rand -base64 24)" "$(openssl rand -base64 24)" > .env
```

Do it before the first start: the PostgreSQL password is written into its volume when the volume
is created, and an existing volume keeps the old one. MinIO reads its root credentials from the
environment on every start, so those can still be changed later.

## Releases

Versions follow SemVer, tagged `v1.0.0`. The tag builds the image, publishes it to GHCR as `X.Y.Z`
and `latest`, and creates a release with `docker-compose.release.yml`, `.env.example` and the
executable jar from inside the image.

That jar runs without Docker, against your own PostgreSQL and MinIO:

```bash
DB_URL=jdbc:postgresql://localhost:5432/lockbox STORAGE_ENDPOINT=http://localhost:9000 \
  java -jar lockbox-1.0.0.jar
```

## Architecture

Envelope encryption: the master key never encrypts the data itself; it only wraps the entry keys.
That is why changing the password costs a few kilobytes instead of re-encrypting the whole vault.

```
master password ─┬─ BCrypt ────────────────────> password_hash in the database
                 └─ Argon2id + key_salt ───────> master key (session memory only)
                                                      │
entry has its own random key (DEK) <─── wrapped ──────┘
   ├─ field values   ── AES-256-GCM ──> bytea in PostgreSQL
   └─ file key       ── wrapped ──> file ── LBX1, 1 MiB chunks ──> MinIO
```

- The key is derived with **Argon2id** (64 MiB, 3 passes, per-user salt), while the password is
  verified against a separate BCrypt hash – the two share no material.
- Data is encrypted with **AES-256-GCM**: a flipped byte in the database or in the bucket does
  not decrypt into garbage; it fails the decryption outright.
- Files are encrypted **as a stream** in a purpose-built `LBX1` format: the header, the chunk
  number and the final-chunk flag all go into the AAD, so chunks cannot be reordered, dropped or
  truncated. Memory use does not depend on the file size.
- Field values and file contents are encrypted. Entry titles, field labels and file names are
  not – otherwise search and sorting would be impossible.

| Package    | Responsibility                                                    |
|------------|-------------------------------------------------------------------|
| `crypto`   | Argon2id, AES-256-GCM, the streaming format, key wrapping         |
| `security` | Sign-in, the master key in the session, Spring Security rules     |
| `user`     | Registration, master password change, password history            |
| `vault`    | Entries, fields, file fields, search, sweeping unfinished uploads |
| `storage`  | S3 client, multipart upload, streaming reads and writes           |
| `backup`   | Export and import of the encrypted archive                        |
| `ui`       | Vaadin views and components                                       |
| `i18n`     | Locales, the remembered language preference, translation files    |

## Development

JDK 21 and Docker are required. Node.js is not: in development mode Vaadin uses its bundled
frontend, and the production build downloads Node on its own inside Docker.

```bash
docker compose up postgres minio -d
./mvnw spring-boot:run
```

| Command                      | What it does                                     |
|------------------------------|--------------------------------------------------|
| `./mvnw verify`              | Everything: style, unit tests, integration tests |
| `./mvnw verify -DskipITs`    | Unit tests only                                  |
| `./mvnw checkstyle:check`    | Style only                                       |
| `./mvnw package -DskipTests` | Build the jar without tests                      |

Flyway owns the schema (`src/main/resources/db/migration`); Hibernate runs with
`ddl-auto: validate` and merely checks the entities against it. Checkstyle is bound to the
`validate` phase, so the build fails before compilation rather than after it.

## Tests

```bash
./mvnw verify
```

Unit tests plus integration tests on real PostgreSQL and MinIO through Testcontainers; without
Docker the integration ones are skipped and the build still passes. They check the properties of
the scheme rather than the happy path: no plaintext in the `value` column, a tampered chunk
rejected on download, ciphertext untouched by a master password change, a backup that does not
open with the master password.

## Project layout

```
src/main/java/dev/lockbox/    backup, crypto, i18n, security, storage, ui, user, vault
src/main/resources/
├── application.yml           settings and their defaults
├── db/migration/             Flyway migrations
├── META-INF/resources/       styles/theme.css – accent colour and dark theme
├── messages.properties       English interface texts
└── messages_ru.properties    Russian interface texts
src/test/java/dev/lockbox/    *Test – unit tests, *IT – integration tests
.github/workflows/
├── ci.yml                    Style, Build, Docker image, Unit tests, Integration tests
└── release.yml               image in GHCR and a release on a v* tag
checkstyle.xml                style rules
docker-compose.yml            build from source
docker-compose.release.yml    run the published image
```

## License

[MIT](LICENSE)
