# Root cause and fix

Root cause:

- Expensive work in cell update path runs on JavaFX Application Thread.

Fix:

1) Precompute display strings ("render tokens") in background threads:
   - timestamp formatting
   - currency formatting
   - summary formatting
2) Keep TableView cells cheap:
   - updateItem does setText only
3) Batch + throttle UI updates:
   - push precomputed rows to a queue from background
   - flush to ObservableList in limited batches on FX thread (~30 fps)

Verification:

- Load GOOD: UI remains responsive while data arrives progressively.
- JFR should show reduced time spent on FX thread in cell update path.
