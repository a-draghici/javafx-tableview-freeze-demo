# JavaFX TableView Freeze Demo (Enterprise-like, Maven, cross-platform)

Demo repo for diagnosing and fixing JavaFX UI TableView freezes using a repeatable workflow: **repro → evidence (thread dump + JFR) → root cause → fix → verification**.

This repository is intentionally small and enterprise-friendly: it shows how I work on UI freeze issues, not a full product.

This repository demonstrates a *common enterprise JavaFX performance/freeze problem*:

--

## What this demo contains

- A minimal JavaFX app with:
    - a **bad** button that triggers building a `TableView` with an **expensive cell factory** (work done in `updateItem`) causing UI jank/freezes.
    - a **good** button that runs the same work based on **background precomputation** + **batching** + **throttling** of UI updates, that keeps the UI responsive

- `profiling-notes/` with short, structured notes:
    - `01-repro.md` – environment + steps to reproduce
    - `02-thread-dump.md` – thread dump capture + key evidence
    - `03-jfr.md` – JFR capture + what to look for
    - `04-root-cause-and-fix.md` – root cause + fix summary + verification

---

## Requirements

- JDK 17+ recommended (works best with 17/21)
- Maven 3.8+
- Cross-platform (Windows/macOS/Linux)

---

## Run

```bash
mvn -v
mvn -q javafx:run
```

If you want a packaged jar (not required for the demo):
```bash
mvn -DskipTests clean package
```

---

## How to reproduce

1) Start the app:
- `mvn -q javafx:run`

2) Choose a row count (e.g. **60000**).

3) Click:
- **Load BAD (expensive cells)**

4) Interact:
- scroll quickly, resize the window, select rows

Expected behavior (for the *bad* path):
- noticeable stutter/jank, input lag; on some machines the UI can appear temporarily frozen.

---

## Verify the fix behavior

Click:
- **Load GOOD (precompute + batch + throttle)**

Expected behavior:
- UI remains responsive while data is prepared and progressively rendered.
- Table content appears in chunks; scrolling is much smoother.

---

## Evidence capture (cross-platform)

### 1) Find PID
```bash
jps -l
```

### 2) Thread dump (during jank/freeze)
While the UI is visibly stuttering or unresponsive:
```bash
jcmd <PID> Thread.print > threaddump_table_bad.txt
```

What you typically see:
- `JavaFX Application Thread` spending time in `TableCell.updateItem(...)` and the expensive formatting logic.

### 3) JFR recording (during jank/freeze)
```bash
jcmd <PID> JFR.start name=table settings=profile duration=120s filename=table_bad.jfr
```

What to look for:
- Hot methods on `JavaFX Application Thread` (expensive `updateItem`, string ops, hashing, etc.)
- Allocation pressure triggered by cell updates

---

## Repository layout

```
src/main/java/.../TableViewFreezeApp.java
profiling-notes/
    01-repro.md
    02-thread-dump.md
    03-jfr.md
    04-root-cause-and-fix.md
```

---

## License

Use this demo freely as a template for internal diagnostics and documentation.
