package sortx.ui;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import sortx.core.data.DataRecord;
import sortx.core.data.DataSet;
import sortx.core.data.parser.DataParser;
import sortx.core.data.parser.ParserRegistry;
import sortx.core.rules.RuleSet;
import sortx.core.sort.SortStrategy;
import sortx.core.sort.SortStrategyRegistry;
import sortx.core.stats.StatsService;
import sortx.core.service.SortingService;

import java.io.File;
import java.text.Normalizer;
import java.util.*;

@Lazy
@Component
public class MainUI {
    private final ParserRegistry parserRegistry;
    private final SortStrategyRegistry sortRegistry;
    private final SortingService sortingService;
    private final StatsService statsService;

    private final BorderPane root = new BorderPane();

    private final Label fileLabel = new Label("Nenhum arquivo carregado");
    private final Label problemLabel = new Label("Tipo de problema: -");
    private final Button importBtn = new Button("Importar CSV...");
    private final ComboBox<String> algoCombo = new ComboBox<>();
    private final Button sortBtn = new Button("Ordenar");

    private final RadioButton modeInMemory = new RadioButton("Na memória");
    private final RadioButton modeInPlace = new RadioButton("No arquivo (in-place)");
    private final ToggleGroup modeGroup = new ToggleGroup();

    // Novo: escolha do modo externo
    private final ComboBox<String> externalModeCombo = new ComboBox<>();

    private final TableView<Map<String, Object>> table = new TableView<>();

    private final RuleSet ruleSet = new RuleSet();
    private final RuleEditorPane ruleEditor;
    private final ChartsPane chartsPane;

    // Problem tabs content
    private final Label suggestionLabel = new Label("Sugestão: -");
    private final ComboBox<String> problemAlgoCombo = new ComboBox<>();
    private final TextField capacityField = new TextField();
    private final TableView<Map<String, Object>> selectedItemsTable = new TableView<>();
    private final Label finalResultLabel = new Label("Resultado final: -");
    private final Label totalsLabel = new Label("Totais: peso=0, valor=0");
    private final PieChart valuePie = new PieChart();
    private final PieChart weightPie = new PieChart();
    private final Button exportResultBtn = new Button("Exportar Resultado...");

    // Tabs
    private Tab tabRules;
    private Tab tabCharts;
    private Tab tabProblems;
    private Tab tabResolution;
    private TabPane centerTabs;

    private DataSet currentData = new DataSet();
    private File currentFile;
    private ProblemType currentProblem = ProblemType.SORTING;

    public MainUI(ParserRegistry parserRegistry, SortStrategyRegistry sortRegistry, SortingService sortingService, StatsService statsService) {
        this.parserRegistry = parserRegistry;
        this.sortRegistry = sortRegistry;
        this.sortingService = sortingService;
        this.statsService = statsService;
        this.ruleEditor = new RuleEditorPane(ruleSet);
        this.chartsPane = new ChartsPane(statsService);
        build();
    }

    public Pane getRoot() { return root; }

    private void build() {
        HBox top = new HBox(10);
        top.setPadding(new Insets(10));
        algoCombo.setPrefWidth(180);
        sortBtn.setDisable(true);

        ObservableList<String> algoNames = FXCollections.observableArrayList();
        for (SortStrategy<?> s : sortRegistry.all()) algoNames.add(s.name());
        algoCombo.setItems(algoNames);
        algoCombo.getSelectionModel().selectFirst();

        importBtn.setOnAction(e -> onImport());
        sortBtn.setOnAction(e -> onExecute());

        modeInMemory.setToggleGroup(modeGroup);
        modeInPlace.setToggleGroup(modeGroup);
        modeGroup.selectToggle(modeInMemory);

        // Novo: opções do modo externo
        externalModeCombo.setItems(FXCollections.observableArrayList(
                "Índice em Disco (zero RAM)",
                "Runs externos (chunks)"
        ));
        externalModeCombo.getSelectionModel().selectFirst();

        // Atualiza habilitação/visibilidade quando usuário troca o modo
        modeGroup.selectedToggleProperty().addListener((obs, o, n) -> updateExternalModeControl());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        top.getChildren().addAll(
                importBtn, new Separator(),
                new Label("Algoritmo:"), algoCombo,
                new Separator(),
                new Label("Modo:"), modeInMemory, modeInPlace,
                new Label("Externo:"), externalModeCombo,
                spacer, sortBtn
        );

        root.setTop(top);

        centerTabs = new TabPane();
        Tab tabTable = new Tab("Tabela"); tabTable.setClosable(false);
        tabRules = new Tab("Regras de Ordenação"); tabRules.setClosable(false);
        tabCharts = new Tab("Gráficos"); tabCharts.setClosable(false);
        tabProblems = new Tab("Problemas"); tabProblems.setClosable(false);
        tabResolution = new Tab("Resolução"); tabResolution.setClosable(false);

        tabTable.setContent(buildTableArea());
        tabRules.setContent(ruleEditor.getRoot());
        tabCharts.setContent(chartsPane.getRoot());
        tabProblems.setContent(buildProblemsPane());
        tabResolution.setContent(buildResolutionPane());

        centerTabs.getTabs().addAll(tabTable, tabRules, tabCharts, tabProblems, tabResolution);
        root.setCenter(centerTabs);

        updateTabsForProblemType();
        updateExternalModeControl();

        root.setPadding(new Insets(5));
    }

    private void updateExternalModeControl() {
        boolean isDP = currentProblem == ProblemType.DP;
        boolean inPlace = modeGroup.getSelectedToggle() == modeInPlace;
        externalModeCombo.setDisable(isDP || !inPlace);
        externalModeCombo.setOpacity(externalModeCombo.isDisabled() ? 0.6 : 1.0);
    }

    private Node buildTableArea() {
        VBox v = new VBox(5);
        v.setPadding(new Insets(10));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        v.getChildren().addAll(fileLabel, problemLabel, table);
        return v;
    }

    private Node buildProblemsPane() {
        VBox v = new VBox(10);
        v.setPadding(new Insets(10));
        suggestionLabel.setWrapText(true);

        // Algoritmos disponíveis
        problemAlgoCombo.setItems(FXCollections.observableArrayList(
                "Programação Dinâmica",
                "Divisão e Conquista",
                "Guloso (Greedy)"
        ));
        problemAlgoCombo.getSelectionModel().selectFirst();

        capacityField.setPromptText("Capacidade (peso máximo), ex.: 35");
        capacityField.setPrefColumnCount(10);

        v.getChildren().addAll(
                new Label("Problema: Mochila 0/1"),
                new Label("Sugestão de algoritmo"), suggestionLabel,
                new Separator(), new Label("Escolha o algoritmo"), problemAlgoCombo,
                new Separator(), new Label("Defina a capacidade (peso máximo)"), capacityField
        );
        return v;
    }

    private Node buildResolutionPane() {
        VBox v = new VBox(10);
        v.setPadding(new Insets(10));

        // Tabela de itens selecionados (resultado)
        selectedItemsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);

        exportResultBtn.setOnAction(e -> onExportResult());

        // Gráficos finais (pizza por valor e por peso)
        valuePie.setTitle("Participação por valor");
        weightPie.setTitle("Participação por peso");

        HBox charts = new HBox(10, valuePie, weightPie);

        v.getChildren().addAll(
                new Label("Itens selecionados"), selectedItemsTable,
                new Separator(), totalsLabel, finalResultLabel,
                new Separator(), new Label("Gráficos da solução"), charts,
                new Separator(), exportResultBtn
        );
        return v;
    }

    private void onExportResult() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Arquivo de texto", "*.txt"));
        File f = chooser.showSaveDialog(null);
        if (f == null) return;
        try (java.io.PrintWriter pw = new java.io.PrintWriter(f, java.nio.charset.StandardCharsets.UTF_8)) {
            pw.println("===== Resultado Final =====");
            pw.println(finalResultLabel.getText());
            pw.println(totalsLabel.getText());
            pw.println();
            pw.println("===== Itens Selecionados =====");
            for (Map<String, Object> row : selectedItemsTable.getItems()) {
                pw.println(row.getOrDefault("item", "") + ", peso=" + row.getOrDefault("peso", "") + ", valor=" + row.getOrDefault("valor", ""));
            }
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Erro ao exportar", ex.getMessage());
        }
    }

    private void updateTabsForProblemType() {
        boolean isDP = currentProblem == ProblemType.DP;
        // Enable/disable tabs according to problem type
        tabRules.setDisable(isDP);
        tabCharts.setDisable(isDP);
        tabProblems.setDisable(!isDP);
        tabResolution.setDisable(!isDP);

        // Controles de memória só fazem sentido para Ordenação
        setMemoryControlsEnabled(!isDP);
        updateExternalModeControl();
    }

    private void setMemoryControlsEnabled(boolean enabled) {
        modeInMemory.setDisable(!enabled);
        modeInPlace.setDisable(!enabled);
        algoCombo.setDisable(!enabled);
    }

    private void onImport() {
        FileChooser chooser = new FileChooser();
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Arquivos CSV", "*.csv"),
                new FileChooser.ExtensionFilter("Todos os arquivos", "*.*")
        );
        File f = chooser.showOpenDialog(null);
        if (f == null) return;

        DataParser parser = parserRegistry.findFor(f.getName());
        if (parser == null) {
            showAlert(Alert.AlertType.ERROR, "Formato não suportado", "Nenhum parser encontrado para este arquivo.");
            return;
        }
        try {
            currentData = parser.parse(f);
            currentFile = f;
            buildTableColumns(currentData.immutableHeaders());
            populateTable(currentData.getRows());
            fileLabel.setText("Carregado: " + f.getName() + " (" + currentData.getRows().size() + " linhas)");
            ruleEditor.setAvailableColumns(currentData.immutableHeaders());
            ruleEditor.setSampleRows(currentData.getRows());
            chartsPane.setHeaders(currentData.immutableHeaders());
            currentProblem = detectProblemType(currentData.immutableHeaders());
            problemLabel.setText("Tipo de problema: " + (currentProblem == ProblemType.DP ? "Programação Dinâmica" : "Ordenação"));
            sortBtn.setText(currentProblem == ProblemType.DP ? "Resolver" : "Ordenar");

            if (currentProblem == ProblemType.DP) {
                suggestionLabel.setText("Detectado padrão de Mochila/Moeda. Sugestão: Programação Dinâmica (alternativas: Divisão e Conquista, Guloso).");
            } else {
                suggestionLabel.setText("-");
            }
            updateTabsForProblemType();
            sortBtn.setDisable(false);
        } catch (Exception ex) {
            showAlert(Alert.AlertType.ERROR, "Erro ao importar", ex.getMessage());
        }
    }

    private ProblemType detectProblemType(List<String> headers) {
        String lower = String.join(" ", headers).toLowerCase(Locale.ROOT);
        if (lower.contains("peso") && lower.contains("valor") ||
                lower.contains("weight") && lower.contains("value") ||
                lower.contains("capacidade") || lower.contains("capacity")) {
            return ProblemType.DP;
        }
        return ProblemType.SORTING;
    }

    private void onExecute() {
        if (currentData == null || currentData.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Sem dados", "Importe um arquivo antes.");
            return;
        }

        // ===== Modo Problema (DP) =====
        if (currentProblem == ProblemType.DP) {
            String algoChoice = Optional.ofNullable(problemAlgoCombo.getValue()).orElse("Programação Dinâmica");
            int capacity;
            try {
                capacity = Integer.parseInt(Optional.ofNullable(capacityField.getText()).orElse("0").trim());
            } catch (Exception ex) {
                showAlert(Alert.AlertType.WARNING, "Capacidade inválida", "Informe um número inteiro para a capacidade (peso máximo).");
                return;
            }
            if (capacity <= 0) {
                showAlert(Alert.AlertType.WARNING, "Capacidade inválida", "A capacidade deve ser maior que zero.");
                return;
            }

            List<Item> items = parseItems(currentData);
            if (items.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Dados inválidos", "CSV deve conter colunas: item, peso, valor.");
                return;
            }

            Result res;
            switch (algoChoice) {
                case "Divisão e Conquista":
                    res = solveKnapsackDivideConquer(items, capacity);
                    break;
                case "Guloso (Greedy)":
                    res = solveKnapsackGreedy(items, capacity);
                    break;
                default:
                    res = solveKnapsackDP(items, capacity);
            }

            updateResultUI(res);
            centerTabs.getSelectionModel().select(tabResolution);
            return;
        }

        // ===== Modo Ordenação =====
        if (ruleSet.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Sem regras", "Adicione ao menos uma regra de ordenação.");
            return;
        }
        String algo = Optional.ofNullable(algoCombo.getValue()).orElse("QuickSort");
        boolean inPlace = modeGroup.getSelectedToggle() == modeInPlace;

        if (inPlace) {
            try {
                InPlaceCsvSorter.ExternalMode extMode =
                        externalModeCombo.getValue().startsWith("Índice")
                                ? InPlaceCsvSorter.ExternalMode.INDEX
                                : InPlaceCsvSorter.ExternalMode.RUNS;

                InPlaceCsvSorter.sortFile(
                        currentFile,
                        ruleSet,
                        algo,
                        Locale.getDefault(),
                        sortRegistry,
                        extMode
                );
                // Reload para refletir alterações
                DataParser parser = parserRegistry.findFor(currentFile.getName());
                currentData = parser.parse(currentFile);
                buildTableColumns(currentData.immutableHeaders());
                populateTable(currentData.getRows());
                chartsPane.refresh(currentData.getRows());
                ruleEditor.setSampleRows(currentData.getRows());
            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "Erro ao ordenar in-place", ex.getMessage());
            }
        } else {
            sortingService.sort(currentData, ruleSet, algo, Locale.getDefault());
            populateTable(currentData.getRows());
            chartsPane.refresh(currentData.getRows());
            ruleEditor.setSampleRows(currentData.getRows());
        }
    }

    private void buildTableColumns(List<String> headers) {
        table.getColumns().clear();
        for (String h : headers) {
            TableColumn<Map<String, Object>, Object> col = new TableColumn<>(h);
            col.setCellValueFactory(param -> {
                Object val = param.getValue().get(h);
                return new javafx.beans.property.SimpleObjectProperty<>(val);
            });
            table.getColumns().add(col);
        }
    }

    private void populateTable(List<DataRecord> rows) {
        ObservableList<Map<String, Object>> items = FXCollections.observableArrayList();
        for (DataRecord r : rows) items.add(r.asMap());
        table.setItems(items);
    }

    private void showAlert(Alert.AlertType type, String title, String msg) {
        Alert a = new Alert(type);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }

    // ===== Knapsack helpers =====
    private static class Item {
        final String name; final int weight; final int value;
        Item(String name, int weight, int value) { this.name = name; this.weight = weight; this.value = value; }
    }
    private static class Result {
        final List<Item> selected; final int totalValue; final int totalWeight;
        Result(List<Item> selected, int totalValue, int totalWeight) { this.selected = selected; this.totalValue = totalValue; this.totalWeight = totalWeight; }
    }

    private static String norm(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).trim();
    }

    private List<Item> parseItems(DataSet ds) {
        List<String> headers = ds.immutableHeaders();
        String hItem = null, hPeso = null, hValor = null;
        for (String h : headers) {
            String l = norm(h);
            if (hItem == null && (l.contains("item") || l.equals("nome"))) hItem = h;
            if (hPeso == null && (l.contains("peso") || l.contains("weight"))) hPeso = h;
            if (hValor == null && (l.contains("valor") || l.contains("value") || l.contains("preco") || l.contains("preço"))) hValor = h;
        }
        if (hItem == null || hPeso == null || hValor == null) return Collections.emptyList();
        List<Item> items = new ArrayList<>();
        for (DataRecord r : ds.getRows()) {
            Map<String, Object> m = r.asMap();
            String name = Objects.toString(m.get(hItem), "");
            try {
                int w = (int)Math.round(Double.parseDouble(Objects.toString(m.get(hPeso)).replace(',', '.')));
                int v = (int)Math.round(Double.parseDouble(Objects.toString(m.get(hValor)).replace(',', '.')));
                items.add(new Item(name, Math.max(0, w), Math.max(0, v)));
            } catch (Exception ignored) { }
        }
        return items;
    }

    private Result solveKnapsackDP(List<Item> items, int capacity) {
        int n = items.size();
        int[][] dp = new int[n + 1][capacity + 1];
        boolean[][] take = new boolean[n + 1][capacity + 1];
        for (int i = 1; i <= n; i++) {
            Item it = items.get(i - 1);
            for (int c = 0; c <= capacity; c++) {
                int best = dp[i - 1][c];
                boolean tk = false;
                if (it.weight <= c) {
                    int candidate = dp[i - 1][c - it.weight] + it.value;
                    if (candidate > best) { best = candidate; tk = true; }
                }
                dp[i][c] = best; take[i][c] = tk;
            }
        }
        int c = capacity;
        List<Item> selected = new ArrayList<>();
        for (int i = n; i >= 1; i--) {
            if (take[i][c]) { Item it = items.get(i - 1); selected.add(it); c -= it.weight; }
        }
        Collections.reverse(selected);
        int totalW = selected.stream().mapToInt(it -> it.weight).sum();
        int totalV = dp[n][capacity];
        return new Result(selected, totalV, totalW);
    }

    private Result solveKnapsackDivideConquer(List<Item> items, int capacity) {
        int n = items.size();
        int[][] memo = new int[n][capacity + 1];
        for (int[] row : memo) Arrays.fill(row, -1);
        boolean[][] choose = new boolean[n][capacity + 1];
        java.util.function.BiFunction<Integer, Integer, Integer> f = new java.util.function.BiFunction<>() {
            @Override public Integer apply(Integer i, Integer c) {
                if (i == n || c == 0) return 0;
                if (memo[i][c] != -1) return memo[i][c];
                Item it = items.get(i);
                int notake = this.apply(i + 1, c);
                int takev = -1;
                if (it.weight <= c) takev = it.value + this.apply(i + 1, c - it.weight);
                int best = Math.max(notake, takev);
                choose[i][c] = (takev > notake);
                return memo[i][c] = best;
            }
        };
        int best = f.apply(0, capacity);
        List<Item> selected = new ArrayList<>();
        int i = 0, c = capacity;
        while (i < n && c >= 0) {
            if (choose[i][c]) { Item it = items.get(i); selected.add(it); c -= it.weight; }
            i++;
        }
        int totalW = selected.stream().mapToInt(it -> it.weight).sum();
        return new Result(selected, best, totalW);
    }

    private Result solveKnapsackGreedy(List<Item> items, int capacity) {
        // Heurística por razão valor/peso (0/1, não garante ótimo)
        List<Item> sorted = new ArrayList<>(items);
        sorted.sort((a, b) -> Double.compare((double)b.value / Math.max(1,b.weight), (double)a.value / Math.max(1,a.weight)));
        List<Item> selected = new ArrayList<>();
        int cap = capacity; int value = 0; int weight = 0;
        for (Item it : sorted) {
            if (it.weight <= cap) { selected.add(it); cap -= it.weight; weight += it.weight; value += it.value; }
        }
        return new Result(selected, value, weight);
    }

    private void updateResultUI(Result res) {
        if (selectedItemsTable.getColumns().isEmpty()) {
            selectedItemsTable.getColumns().clear();
            TableColumn<Map<String, Object>, Object> cItem = new TableColumn<>("item");
            cItem.setCellValueFactory(param -> new javafx.beans.property.SimpleObjectProperty<>(param.getValue().get("item")));
            TableColumn<Map<String, Object>, Object> cPeso = new TableColumn<>("peso");
            cPeso.setCellValueFactory(param -> new javafx.beans.property.SimpleObjectProperty<>(param.getValue().get("peso")));
            TableColumn<Map<String, Object>, Object> cValor = new TableColumn<>("valor");
            cValor.setCellValueFactory(param -> new javafx.beans.property.SimpleObjectProperty<>(param.getValue().get("valor")));
            selectedItemsTable.getColumns().addAll(cItem, cPeso, cValor);
        }
        ObservableList<Map<String, Object>> items = FXCollections.observableArrayList();
        for (Item it : res.selected) {
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("item", it.name); row.put("peso", it.weight); row.put("valor", it.value);
            items.add(row);
        }
        selectedItemsTable.setItems(items);

        totalsLabel.setText("Totais: peso=" + res.totalWeight + ", valor=" + res.totalValue);
        finalResultLabel.setText("Resultado final: valor máximo = " + res.totalValue + ", capacidade utilizada = " + res.totalWeight);

        valuePie.getData().clear();
        weightPie.getData().clear();
        for (Item it : res.selected) {
            valuePie.getData().add(new PieChart.Data(it.name, it.value));
            weightPie.getData().add(new PieChart.Data(it.name, it.weight));
        }
    }

    private enum ProblemType { SORTING, DP }
}
