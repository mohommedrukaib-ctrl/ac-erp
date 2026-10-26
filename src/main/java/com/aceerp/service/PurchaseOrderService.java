package com.aceerp.service;

import com.aceerp.model.PurchaseOrder;
import com.aceerp.repository.PurchaseOrderRepository;
import java.sql.SQLException;
import java.util.List;

public class PurchaseOrderService {
    private final PurchaseOrderRepository repository = new PurchaseOrderRepository();

    public List<PurchaseOrder> getAllPurchaseOrders() {
        return repository.findAll();
    }

    public PurchaseOrder savePurchaseOrder(PurchaseOrder po) {
        return repository.save(po);
    }

    public void deletePurchaseOrder(int id) throws SQLException {
        repository.delete(id);
    }

    public String generateNextPoNumber() {
        return repository.generateNextPoNumber();
    }
}