package com.aceerp.service;

import com.aceerp.model.Invoice;
import com.aceerp.model.InvoiceLine;
import com.aceerp.repository.InvoiceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public class InvoiceService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceService.class);
    private final InvoiceRepository invoiceRepository = new InvoiceRepository();

    public List<Invoice> listAll() {
        return invoiceRepository.findAll();
    }

    public Optional<Invoice> findById(int id) {
        return invoiceRepository.findById(id);
    }

    public Invoice saveInvoice(Invoice invoice) {
        if (invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().isBlank()) {
            invoice.setInvoiceNumber(generateNextInvoiceNumber());
        }
        recalculateTotals(invoice);
        return invoiceRepository.save(invoice);
    }

    public String generateNextInvoiceNumber() {
        return "INV-" + (System.currentTimeMillis() / 1000);
    }

    public void recalculateTotals(Invoice invoice) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;

        for (InvoiceLine line : invoice.getLines()) {
            line.recalculateTotal();
            if (line.getTotalPrice() != null) {
                subtotal = subtotal.add(line.getQuantity().multiply(line.getUnitPrice()));
                if (line.getTaxRate() != null && line.getTaxRate().compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal tax = line.getQuantity().multiply(line.getUnitPrice())
                            .multiply(line.getTaxRate().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
                    taxTotal = taxTotal.add(tax);
                }
            }
        }

        invoice.setSubtotal(subtotal.setScale(2, RoundingMode.HALF_UP));
        invoice.setTaxAmount(taxTotal.setScale(2, RoundingMode.HALF_UP));
        invoice.setTotalAmount(subtotal.add(taxTotal).setScale(2, RoundingMode.HALF_UP));

        updatePaymentStatus(invoice);
    }

    public void recordPayment(Invoice invoice, BigDecimal paymentAmount) {
        if (paymentAmount == null || paymentAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }

        BigDecimal currentPaid = invoice.getPaidAmount() != null ? invoice.getPaidAmount() : BigDecimal.ZERO;
        BigDecimal newPaid = currentPaid.add(paymentAmount);
        invoice.setPaidAmount(newPaid);

        updatePaymentStatus(invoice);
        saveInvoice(invoice);
    }

    private void updatePaymentStatus(Invoice invoice) {
        BigDecimal total = invoice.getTotalAmount() != null ? invoice.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal paid = invoice.getPaidAmount() != null ? invoice.getPaidAmount() : BigDecimal.ZERO;

        if (paid.compareTo(BigDecimal.ZERO) <= 0) {
            invoice.setStatus("UNPAID");
        } else if (paid.compareTo(total) >= 0) {
            invoice.setStatus("PAID");
        } else {
            invoice.setStatus("PARTIAL");
        }
    }
}