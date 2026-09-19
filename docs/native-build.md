# Native build notes

Notes for `feature/native-build`: how the native executable is built, and what it changes compared
with the JVM application.

## How it is built

- `Dockerfile.native` is a two stage build. Stage one compiles the application with Mandrel (Red Hat's
  GraalVM distribution) through the pom's `native` profile. Stage two copies only the resulting binary
  onto a micro runtime image, so no JVM, Maven or sources ship.
- Without Docker, the same executable comes from
  `./mvnw package -Dnative -Dquarkus.native.container-build=true -DskipTests`. The build runs inside a
  Mandrel builder container, so no local GraalVM install is needed.
- The CI job `native-build` in `.github/workflows/ci.yml` does both, builds the executable and checks that
  `Dockerfile.native` builds, on every push and pull request. It is separate from the test job because it
  takes around ten minutes.

## Measurements

Produced by `scripts/measure-startup.sh`, run by the CI job on the same runner, against the same
PostgreSQL 16 service, with the same load for both modes: 300 sequential requests cycling over
`/q/health/ready`, `/q/openapi` and a secured endpoint (401).

| Mode | Ready after (ms) | Startup reported by Quarkus (s) | RSS after the load (MB) |
|---|---|---|---|
| JVM (`java -jar quarkus-run.jar`) | 4021 | 3.832 | 249 |
| Native executable | 156 | 0.126 | 127 |

The native executable is about 103 MB on disk.

Reading the figures:

- Startup is roughly 25 times faster in native mode. The JVM has to load and link classes and JIT compile
  at start, while the native binary already has its heap initialised and its code compiled.
- Memory after the load is about half. It is a single run on a shared CI runner, so treat the ratios as an
  order of magnitude and not as a benchmark. The JVM figure would keep changing with more traffic, since
  the JIT and the heap grow with it.
- The JVM run is the first to start against the empty database and so applies the Flyway migrations, while
  the native run only validates them. That favours native by a few tens of milliseconds at most.
- What is given up: native builds take minutes and several GB of memory, the binary is tied to one
  platform, and peak throughput after a long warm up is usually better on the JVM.

## Issues found while getting it to compile

- A `static final SecureRandom` in `AuthServiceImpl` failed the native build, because it was created while
  the image was built and its seed would have been frozen into the image. It is now a field of the bean, created
  when the application starts. This is a good example of the closed world model: code that runs at class
  initialisation time is executed at build time in native mode.

## Building locally

Compiling a native image needs several GB of memory in the builder container (the CI build peaks at about
3.4 GB). On the development machine used for this tutorial, Docker Desktop was limited to 1.4 GiB, and the
build was killed by the out of memory killer, so the executable was built and measured in CI instead. Raise
the memory of the Docker VM to at least 6 GB to build locally.
