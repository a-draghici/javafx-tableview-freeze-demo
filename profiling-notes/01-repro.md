# Repro

Environment:
- OS: Windows 10 Enterprise LTSC x64 21H2
- JDK: Liberica 21.0.10
- JavaFX: 21
- Maven: 3.9.5

Run:
- mvn -q javafx:run

Steps:
1. Set Rows = 60000 (or higher)
2. Click "Load BAD (expensive cells)"
3. Immediately scroll quickly and resize the window

Observed (BAD):
- stutter/jank and noticeable input lag
- in some environments: temporary "unresponsive" feeling

Expected:
- UI stays responsive while data is displayed
