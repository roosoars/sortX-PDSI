package sortx.ui;

import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.chart.*;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import sortx.core.data.DataRecord;
import sortx.core.stats.StatsService;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class ChartsPane {
    private final StatsService stats;

    private final BorderPane root = new BorderPane();
    private final ComboBox<String> fieldCombo = new ComboBox<>();
    private final Label statsLabel = new Label("Sem dados");
    private List<DataRecord> lastRows;

    public ChartsPane(StatsService stats) {
        this.stats = stats;
        build();
    }

    public Node getRoot() { return root; }

    public void setHeaders(List<String> headers) {
        fieldCombo.setItems(FXCollections.observableArrayList(headers));
        if (!headers.isEmpty()) fieldCombo.getSelectionModel().selectFirst();
    }

    public void refresh(List<DataRecord> rows) {
        lastRows = rows;
        updateCharts();
    }

    private void build() {
        VBox top = new VBox(8);
        top.setPadding(new Insets(10));
        top.getChildren().addAll(new Label("Campo para estatísticas/gráficos:"), fieldCombo, statsLabel);
        root.setTop(top);

        fieldCombo.setOnAction(e -> updateCharts());
    }

    private void updateCharts() {
        if (lastRows == null || lastRows.isEmpty() || fieldCombo.getValue() == null) {
            root.setCenter(new Label("Sem dados"));
            statsLabel.setText("Sem dados");
            return;
        }
        String col = fieldCombo.getValue();

        VBox box = new VBox(10);
        box.setPadding(new Insets(10));

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        BarChart<String, Number> bar = new BarChart<>(xAxis, yAxis);
        bar.setTitle("Top 10 frequências: " + col);
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        Map<String, Long> freq = stats.frequency(lastRows, col, 10);
        for (Map.Entry<String, Long> e : freq.entrySet()) {
            series.getData().add(new XYChart.Data<>(e.getKey(), e.getValue()));
        }
        bar.getData().add(series);
        box.getChildren().addAll(bar);

        var ns = stats.computeForNumeric(lastRows, col);
        if (ns.count() > 0) {
            NumberAxis lx = new NumberAxis();
            NumberAxis ly = new NumberAxis();
            LineChart<Number, Number> line = new LineChart<>(lx, ly);
            line.setTitle("Valores numéricos (ordenados) — " + col);
            XYChart.Series<Number, Number> s = new XYChart.Series<>();

            List<Double> values = new ArrayList<>();
            for (DataRecord r : lastRows) {
                Object o = r.asMap().get(col);
                try { values.add(Double.parseDouble(o.toString().replace(",", "."))); } catch (Exception ignored) { }
            }
            values.sort(Comparator.naturalOrder());

            int idx = 0;
            for (double v : values) {
                s.getData().add(new XYChart.Data<>(idx++, v));
            }
            line.getData().add(s);

            String txt = String.format("N=%d | min=%.4f | max=%.4f | média=%.4f | mediana=%.4f | desvio=%.4f",
                    ns.count(), ns.min(), ns.max(), ns.mean(), ns.median(), ns.stddev());
            statsLabel.setText(txt);
            box.getChildren().addAll(line);
        } else {
            statsLabel.setText("Sem estatísticas numéricas para este campo.");
        }

        root.setCenter(box);
    }
}
