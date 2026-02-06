package ro.a3y.demo.freezedemo;

import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Enterprise-realistic demo:
 * <p>
 * BAD:
 *  - TableView with expensive cell factories (heavy work in updateItem)
 *  - bulk-load on FX thread
 *  => jank/freezes under load
 */
public class TableViewFreezeApp extends Application {

    public static final class RawRow {
        public final long id;
        public final long epochMillis;
        public final double amount;
        public final String customer;
        public final String payload;

        public RawRow(long id, long epochMillis, double amount, String customer, String payload) {
            this.id = id;
            this.epochMillis = epochMillis;
            this.amount = amount;
            this.customer = customer;
            this.payload = payload;
        }
    }

    public static final class RenderRow {
        private final LongProperty id = new SimpleLongProperty();
        private final StringProperty ts = new SimpleStringProperty();
        private final StringProperty amount = new SimpleStringProperty();
        private final StringProperty customer = new SimpleStringProperty();
        private final StringProperty summary = new SimpleStringProperty();

        public RenderRow(long id, String ts, String amount, String customer, String summary) {
            this.id.set(id);
            this.ts.set(ts);
            this.amount.set(amount);
            this.customer.set(customer);
            this.summary.set(summary);
        }

        public long getId() { return id.get(); }
        public LongProperty idProperty() { return id; }
        public StringProperty tsProperty() { return ts; }
        public StringProperty amountProperty() { return amount; }
        public StringProperty customerProperty() { return customer; }
        public StringProperty summaryProperty() { return summary; }
    }

    private final ObservableList<Object> backing = FXCollections.observableArrayList();
    private final TableView<Object> table = new TableView<>(backing);

    private final Label status = new Label("Ready.");
    private final ProgressIndicator progress = new ProgressIndicator();
    private final AtomicBoolean running = new AtomicBoolean(false);

    private final ConcurrentLinkedQueue<RenderRow> uiQueue = new ConcurrentLinkedQueue<>();
    private AnimationTimer uiFlusher;

    private final ExecutorService pool = Executors.newFixedThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors() / 2),
            r -> {
                Thread t = new Thread(r, "bg-precompute");
                t.setDaemon(true);
                return t;
            }
    );

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
            .withZone(ZoneId.systemDefault());
    private static final ThreadLocal<NumberFormat> MONEY_FMT = ThreadLocal.withInitial(() -> {
        NumberFormat nf = NumberFormat.getCurrencyInstance(Locale.GERMANY);
        nf.setMaximumFractionDigits(2);
        nf.setMinimumFractionDigits(2);
        return nf;
    });

    @Override
    public void start(Stage stage) {
        progress.setVisible(false);

        TableColumn<Object, String> idCol = new TableColumn<>("ID");
        idCol.setPrefWidth(90);

        TableColumn<Object, String> tsCol = new TableColumn<>("Timestamp");
        tsCol.setPrefWidth(190);

        TableColumn<Object, String> amountCol = new TableColumn<>("Amount");
        amountCol.setPrefWidth(120);

        TableColumn<Object, String> custCol = new TableColumn<>("Customer");
        custCol.setPrefWidth(160);

        TableColumn<Object, String> sumCol = new TableColumn<>("Summary");
        sumCol.setPrefWidth(420);

        table.getColumns().addAll(idCol, tsCol, amountCol, custCol, sumCol);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("No data."));

        TextField countField = new TextField("60000");
        countField.setPrefWidth(100);

        Button badBtn = new Button("Load BAD (expensive cells)");
        badBtn.setOnAction(e -> loadBad(parseCount(countField.getText()), idCol, tsCol, amountCol, custCol, sumCol));

        Button clearBtn = new Button("Clear");
        clearBtn.setOnAction(e -> {
            backing.clear();
            status.setText("Cleared.");
        });

        HBox controls = new HBox(10,
                new Label("Rows:"), countField,
                badBtn, clearBtn,
                progress, status
        );
        controls.setPadding(new Insets(10));

        VBox root = new VBox(10, controls, table);
        VBox.setVgrow(table, Priority.ALWAYS);
        root.setPadding(new Insets(10));

        stage.setTitle("JavaFX TableView Freeze Demo (Enterprise-like)");
        stage.setScene(new Scene(root, 1050, 700));
        stage.show();

        uiFlusher = new AnimationTimer() {
            private long last = 0;
            @Override
            public void handle(long now) {
                if (now - last < 33_000_000L) return; // ~30 fps
                last = now;
                flushUiQueue(1200);
            }
        };
        uiFlusher.start();
    }

    private int parseCount(String s) {
        try { return Math.max(1, Integer.parseInt(s.trim())); }
        catch (Exception ex) { return 60000; }
    }

    private void loadBad(int count,
                         TableColumn<Object, String> idCol,
                         TableColumn<Object, String> tsCol,
                         TableColumn<Object, String> amountCol,
                         TableColumn<Object, String> custCol,
                         TableColumn<Object, String> sumCol) {

        if (!running.compareAndSet(false, true)) return;

        status.setText("Loading BAD... (expect jank/freezes)");
        progress.setVisible(true);
        backing.clear();

        idCol.setCellValueFactory(cd -> new SimpleStringProperty(extractId(cd.getValue())));
        tsCol.setCellValueFactory(cd -> new SimpleStringProperty(extractTs(cd.getValue())));
        amountCol.setCellValueFactory(cd -> new SimpleStringProperty(extractAmount(cd.getValue())));
        custCol.setCellValueFactory(cd -> new SimpleStringProperty(extractCustomer(cd.getValue())));
        sumCol.setCellValueFactory(cd -> new SimpleStringProperty(extractSummary(cd.getValue())));

        installBadCellFactories(idCol, tsCol, amountCol, custCol, sumCol);

        long t0 = System.nanoTime();
        for (int i = 0; i < count; i++) {
            backing.add(makeRawRow(i));
        }
        long t1 = System.nanoTime();

        status.setText("BAD loaded " + count + " rows in ~" + millis(t0, t1) + " ms. Try scrolling/resizing.");
        progress.setVisible(false);
        running.set(false);
    }

    private void installBadCellFactories(TableColumn<Object, String> idCol,
                                         TableColumn<Object, String> tsCol,
                                         TableColumn<Object, String> amountCol,
                                         TableColumn<Object, String> custCol,
                                         TableColumn<Object, String> sumCol) {
        idCol.setCellFactory(col -> expensiveCell());
        tsCol.setCellFactory(col -> expensiveCell());
        amountCol.setCellFactory(col -> expensiveCell());
        custCol.setCellFactory(col -> expensiveCell());
        sumCol.setCellFactory(col -> expensiveCell());
    }

    private TableCell<Object, String> expensiveCell() {
        return new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }

                String s = item;

                int h = 0;
                for (int i = 0; i < 220; i++) {
                    h = 31 * h + s.hashCode();
                }
                if ((h & 1) == 0) {
                    s = s.toUpperCase(Locale.ROOT);
                }
                s = s.replace('_', '-');

                setText(s);
            }
        };
    }

    private String buildSummary(String payload) {
        int max = 120;
        String s = payload.length() > max ? payload.substring(0, max) + "..." : payload;

        int h = 0;
        for (int i = 0; i < 90; i++) h = 31 * h + s.hashCode();
        if ((h & 1) == 0) s = s.replace('_', '-');
        return s;
    }

    private void flushUiQueue(int maxPerFlush) {
        if (uiQueue.isEmpty()) return;

        List<Object> batch = new ArrayList<>(Math.min(maxPerFlush, 2000));
        for (int i = 0; i < maxPerFlush; i++) {
            RenderRow rr = uiQueue.poll();
            if (rr == null) break;
            batch.add(rr);
        }
        if (!batch.isEmpty()) {
            backing.addAll(batch);
        }
    }

    private RawRow makeRawRow(long id) {
        long now = System.currentTimeMillis();
        double amt = (id % 1000) * 1.37;
        String customer = "Customer-" + (id % 500);
        String payload = "payload_" + id + "_" + UUID.randomUUID() + "_"
                + "lorem_ipsum_dolor_sit_amet_consectetur_adipiscing_elit_"
                + "sed_do_eiusmod_tempor_incididunt_ut_labore_et_dolore_magna_aliqua";
        return new RawRow(id, now - (id % 10_000), amt, customer, payload);
    }

    private String extractId(Object row) {
        if (row instanceof RawRow r) return Long.toString(r.id);
        if (row instanceof RenderRow rr) return Long.toString(rr.getId());
        return "";
    }

    private String extractTs(Object row) {
        if (row instanceof RawRow r) return TS_FMT.format(Instant.ofEpochMilli(r.epochMillis));
        if (row instanceof RenderRow rr) return rr.tsProperty().get();
        return "";
    }

    private String extractAmount(Object row) {
        if (row instanceof RawRow r) return MONEY_FMT.get().format(r.amount);
        if (row instanceof RenderRow rr) return rr.amountProperty().get();
        return "";
    }

    private String extractCustomer(Object row) {
        if (row instanceof RawRow r) return r.customer;
        if (row instanceof RenderRow rr) return rr.customerProperty().get();
        return "";
    }

    private String extractSummary(Object row) {
        if (row instanceof RawRow r) return buildSummary(r.payload);
        if (row instanceof RenderRow rr) return rr.summaryProperty().get();
        return "";
    }

    private static long millis(long t0, long t1) {
        return TimeUnit.NANOSECONDS.toMillis(t1 - t0);
    }

    @Override
    public void stop() {
        pool.shutdownNow();
        if (uiFlusher != null) uiFlusher.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
