# JFR evidence

During BAD run:

- jcmd <PID> JFR.start name=table settings=profile duration=120s filename=table_bad.jfr

What to look for in JFR:

- Hot methods on "JavaFX Application Thread":
  - expensiveCell().updateItem(...)
  - String.hashCode / toUpperCase / replace
- Allocation pressure during scrolling / rendering
- Optional: lock contention (if present)

Note:
- JFR is usually enough to justify moving work out of the cell update path.
