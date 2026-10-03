# Mercury Compose Hardener

Mercury is a Java command-line tool that converts Docker Compose files into a
more restrictive deployment configuration. It reads approved image digests
from a lockfile and honors service exceptions explicitly recorded in a security
dossier.

## What It Does

- Rewrites published ports to bind only on `127.0.0.1`.
- Replaces mutable image tags with SHA-256-pinned images from the lockfile.
- Sets non-exempt services to user `1000` and drops all Linux capabilities.
- Applies dossier exceptions without changing those services.
- Writes a hardened Compose file and a machine-readable JSON summary.
- Fails closed when a strict service has no valid digest in the lockfile.

## Build And Run

Requires Java 17 or newer. From the CLI project directory:

```sh
cd environment/mercury-cli
./gradlew test
./gradlew run
```

By default, the CLI reads `../fixtures/docker-compose.yml` and
`compose-images.json`, looks for `../docs/mercury-security-dossier.md`, and
writes `../output/hardened-compose.yml` plus
`../output/hardening-report.json`. If no default dossier is present, no
exceptions are applied. Inputs can also be supplied positionally:

```sh
./gradlew run --args="../fixtures/docker-compose.yml ../output/hardened-compose.yml compose-images.json ../docs/mercury-security-dossier.md ../output/hardening-report.json"
```

The report schema is intentionally small and stable:

```json
{
	"services_processed": 2,
	"exceptions_granted": 0
}
```

## Design Notes

Jackson handles YAML and JSON parsing, while the hardening logic is isolated
from file I/O so it can be tested against service maps directly. Dossier
exceptions are recognized from explicit directives such as `Exempt
'legacy-cache'`, `service 'legacy-cache' is exempt`, and `approved exception for
service 'legacy-cache'`.

This tool focuses on the hardening rules in the project brief; it does not
validate every Docker Compose feature or replace image provenance review. Image
digests must be maintained in the lockfile, and exceptions should be narrow,
documented, and time-bound.