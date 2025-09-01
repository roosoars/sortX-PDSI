package sortx.core.rules;

public class SortRule {
    private String column;
    private ColumnType type;
    private SortOrder order;
    private boolean nullsFirst;
    private boolean caseInsensitive;

    public SortRule() { }

    public SortRule(String column, ColumnType type, SortOrder order, boolean nullsFirst, boolean caseInsensitive) {
        this.column = column;
        this.type = type;
        this.order = order;
        this.nullsFirst = nullsFirst;
        this.caseInsensitive = caseInsensitive;
    }

    public String getColumn() { return column; }
    public void setColumn(String column) { this.column = column; }

    public ColumnType getType() { return type; }
    public void setType(ColumnType type) { this.type = type; }

    public SortOrder getOrder() { return order; }
    public void setOrder(SortOrder order) { this.order = order; }

    public boolean isNullsFirst() { return nullsFirst; }
    public void setNullsFirst(boolean nullsFirst) { this.nullsFirst = nullsFirst; }

    public boolean isCaseInsensitive() { return caseInsensitive; }
    public void setCaseInsensitive(boolean caseInsensitive) { this.caseInsensitive = caseInsensitive; }
}
