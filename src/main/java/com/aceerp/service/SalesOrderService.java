package com.aceerp.service;

import com.aceerp.model.Quotation;
import com.aceerp.model.QuotationLine;
import com.aceerp.model.SalesOrder;
import com.aceerp.model.SalesOrderLine;
import com.aceerp.repository.SalesOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class SalesOrderService {

    private static final Logger log = LoggerFactory.getLogger(SalesOrderService.class);
    private final SalesOrderRepository repository = new SalesOrderRepository();

    public List<SalesOrder> listAll() {
        return repository.findAll();
    }

    public Optional<SalesOrder> findById(int id) {
        return repository.findById(id);
    }

    public SalesOrder save(SalesOrder so) {
        if (so.getSoNumber() == null || so.getSoNumber().isBlank()) {
            so.setSoNumber(generateNextSONumber());
        }
        so.recalculateTotals();
        return repository.save(so);
    }

    public String generateNextSONumber() {
        return "SO-" + (System.currentTimeMillis() / 1000);
    }

    public SalesOrder convertFromQuotation(Quotation quote) {
        SalesOrder so = new SalesOrder();
        so.setSoNumber(generateNextSONumber());
        so.setQuotationId(quote.getId());
        so.setCustomerId(quote.getCustomerId());
        so.setCustomerName(quote.getCustomerName());
        so.setSubtotal(quote.getSubtotal());
        so.setTaxAmount(quote.getTaxAmount());
        so.setTotalAmount(quote.getTotalAmount());
        so.setNotes("Converted from Quotation #" + quote.getQuoteNumber());

        List<SalesOrderLine> lines = new ArrayList<>();
        for (QuotationLine qLine : quote.getLines()) {
            SalesOrderLine sol = new SalesOrderLine();
            sol.setProductId(qLine.getProductId());
            sol.setDescription(qLine.getDescription());
            sol.setQuantity(qLine.getQuantity());
            sol.setUnitPrice(qLine.getUnitPrice());
            sol.setTotalPrice(qLine.getTotalPrice());
            lines.add(sol);
        }
        so.setLines(lines);
        return save(so);
    }
}