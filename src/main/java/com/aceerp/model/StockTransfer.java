package com.aceerp.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class StockTransfer {
    private int id;
    private String transferNumber;
    private int sourceWarehouseId;
    private String sourceWarehouseName;
    private int destinationWarehouseId;
    private String destinationWarehouseName;
    private LocalDate transferDate = LocalDate.now();
    private String status = "COMPLETED";
    private String notes;
    private List<StockTransferLine> lines = new ArrayList<>();

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getTransferNumber() { return transferNumber; }
    public void setTransferNumber(String transferNumber) { this.transferNumber = transferNumber; }

    public int getSourceWarehouseId() { return sourceWarehouseId; }
    public void setSourceWarehouseId(int sourceWarehouseId) { this.sourceWarehouseId = sourceWarehouseId; }

    public String getSourceWarehouseName() { return sourceWarehouseName; }
    public void setSourceWarehouseName(String sourceWarehouseName) { this.sourceWarehouseName = sourceWarehouseName; }

    public int getDestinationWarehouseId() { return destinationWarehouseId; }
    public void setDestinationWarehouseId(int destinationWarehouseId) { this.destinationWarehouseId = destinationWarehouseId; }

    public String getDestinationWarehouseName() { return destinationWarehouseName; }
    public void setDestinationWarehouseName(String destinationWarehouseName) { this.destinationWarehouseName = destinationWarehouseName; }

    public LocalDate getTransferDate() { return transferDate; }
    public void setTransferDate(LocalDate transferDate) { this.transferDate = transferDate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public List<StockTransferLine> getLines() { return lines; }
    public void setLines(List<StockTransferLine> lines) { this.lines = lines; }
}