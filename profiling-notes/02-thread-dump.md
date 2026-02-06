# Thread dump evidence

1) Find PID:

- jps -l

2) During jank/freeze:

- jcmd <PID> Thread.print > threaddump_table_bad.txt

What to look for:

- "JavaFX Application Thread" spending time in:
  - javafx.scene.control.TableCell.updateItem(...)
  - ro.a3y.demo.freezedemo.TableViewFreezeApp$...expensiveCell(...).updateItem(...)
  - heavy String ops (hashCode, toUpperCase, replace)

Conclusion:
- Expensive work in cell update path runs on FX thread -> UI jank/freezes.
