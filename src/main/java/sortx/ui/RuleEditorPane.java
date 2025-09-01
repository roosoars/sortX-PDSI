package sortx.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import sortx.core.data.DataRecord;
import sortx.core.rules.*;

import java.util.List;

public class RuleEditorPane {
    private final RuleSet ruleSet;

    private final BorderPane root = new BorderPane();

    private final ComboBox<String> colCombo = new ComboBox<>();
    private final ComboBox<SortOrder> orderCombo = new ComboBox<>();
    private final CheckBox caseInsensitive = new CheckBox("Ignorar maiúsc./minúsc.");
    private final Label detectedTypeLabel = new Label("Tipo: -");
    private final Button addBtn = new Button("Adicionar regra");
    private final Button removeBtn = new Button("Remover selecionada");

    private final TableView<SortRule> table = new TableView<>();
    private final ObservableList<SortRule> items = FXCollections.observableArrayList();

    private List<DataRecord> sampleRows;

    public RuleEditorPane(RuleSet ruleSet) {
        this.ruleSet = ruleSet;
        build();
    }

    public Node getRoot() { return root; }

    public void setAvailableColumns(List<String> headers) {
        colCombo.setItems(FXCollections.observableArrayList(headers));
        if (!headers.isEmpty()) colCombo.getSelectionModel().selectFirst();
    }

    public void setSampleRows(List<DataRecord> rows) {
        this.sampleRows = rows;
        updateDetectedType();
    }

    private void build() {
        VBox form = new VBox(8);
        form.setPadding(new Insets(10));
        HBox row1 = new HBox(10);
        HBox row2 = new HBox(10);

        orderCombo.setItems(FXCollections.observableArrayList(SortOrder.values()));
        orderCombo.getSelectionModel().select(SortOrder.ASC);

        colCombo.setOnAction(e -> updateDetectedType());

        row1.getChildren().addAll(new Label("Coluna:"), colCombo, new Label("Ordem:"), orderCombo);
        row2.getChildren().addAll(detectedTypeLabel, caseInsensitive, addBtn, removeBtn);
        form.getChildren().addAll(row1, row2);

        addBtn.setOnAction(e -> onAdd());
        removeBtn.setOnAction(e -> onRemove());

        TableColumn<SortRule, String> c1 = new TableColumn<>("Coluna");
        c1.setCellValueFactory(new PropertyValueFactory<>("column"));
        TableColumn<SortRule, ColumnType> c2 = new TableColumn<>("Tipo");
        c2.setCellValueFactory(new PropertyValueFactory<>("type"));
        TableColumn<SortRule, SortOrder> c3 = new TableColumn<>("Ordem");
        c3.setCellValueFactory(new PropertyValueFactory<>("order"));
        TableColumn<SortRule, Boolean> c5 = new TableColumn<>("Case-insensitive");
        c5.setCellValueFactory(new PropertyValueFactory<>("caseInsensitive"));

        table.getColumns().addAll(c1, c2, c3, c5);
        table.setItems(items);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        VBox center = new VBox(10, form, table);
        center.setPadding(new Insets(10));
        root.setCenter(center);

        Label hint = new Label("Dica: adicione múltiplas regras; se a primeira empatar, a próxima decide (thenComparing).");
        hint.setPadding(new Insets(10));
        root.setBottom(hint);
    }

    private void updateDetectedType() {
        String col = colCombo.getValue();
        if (col == null || sampleRows == null) {
            detectedTypeLabel.setText("Tipo: -");
            return;
        }
        ColumnType inferred = TypeInference.inferForColumn(sampleRows, col);
        detectedTypeLabel.setText("Tipo: " + (inferred == null ? "-" : inferred.toString()));
    }

    private void onAdd() {
        if (colCombo.getValue() == null) return;
        ColumnType inferred = TypeInference.inferForColumn(sampleRows, colCombo.getValue());
        SortRule r = new SortRule(colCombo.getValue(), inferred == null ? ColumnType.STRING : inferred, orderCombo.getValue(), false, caseInsensitive.isSelected());
        ruleSet.add(r);
        items.add(r);
    }

    private void onRemove() {
        SortRule sel = table.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        ruleSet.remove(sel);
        items.remove(sel);
    }
}
