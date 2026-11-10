package com.aceerp.service;

import com.aceerp.model.Invoice;
import com.aceerp.model.InvoiceLine;
import com.aceerp.repository.InvoiceRepository;
import com.aceerp.util.DocumentDiscount;
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
    private final AccountingService accountingService = new AccountingService();

    public List<Invoice> listAll() {
        return invoiceRepository.findAll();
    }

    public Optional<Invoice> findById(int id) {
        return invoiceRepository.findById(id);
    }

    public Invoice saveInvoice(Invoice invoice) {
        boolean isNew = invoice.getId() <= 0;
        if (invoice.getInvoiceNumber() == null || invoice.getInvoiceNumber().isBlank()) {
            invoice.setInvoiceNumber(generateNextInvoiceNumber());
        }
        recalculateTotals(invoice);
        Invoice saved = invoiceRepository.save(invoice);

        // Auto-post to Double-Entry General Ledger
        try {
            accountingService.postCustomerInvoice(
                saved.getId(),
                saved.getInvoiceNumber(),
                saved.getInvoiceDate() != null ? saved.getInvoiceDate() : LocalDate.now(),
                saved.getSubtotal().subtract(saved.getDocumentDiscountAmount()),
                saved.getTaxAmount(),
                saved.getTotalAmount()
            );
        } catch (Exception e) {
            log.error("Failed to auto-post invoice #{} to GL: {}", saved.getInvoiceNumber(), e.getMessage());
        }

        AuditLogService.log(
                isNew ? "CREATE" : "UPDATE",
                "INVOICE",
                saved.getId(),
                "Saved Invoice #" + saved.getInvoiceNumber() + " Total: " + saved.getTotalAmount()
        );

        return saved;
    }

    public String generateNextInvoiceNumber() {
        return "INV-" + (System.currentTimeMillis() / 1000);
    }

    public void recalculateTotals(Invoice invoice) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;

        for (InvoiceLine line : invoice.getLines()) {
            line.recalculateTotal();
            if (line.getTotalPrice() != null) subtotal = subtotal.add(line.getTotalPrice());
            if (line.getTaxAmount() != null) taxTotal = taxTotal.add(line.getTaxAmount());
        }

        recalculateAmounts(invoice, subtotal, taxTotal);
        updatePaymentStatus(invoice);
    }

    public void recalculateAmounts(Invoice invoice) {
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal taxTotal = BigDecimal.ZERO;
        for (InvoiceLine line : invoice.getLines()) {
            line.recalculateTotal();
            if (line.getTotalPrice() != null) subtotal = subtotal.add(line.getTotalPrice());
            if (line.getTaxAmount() != null) taxTotal = taxTotal.add(line.getTaxAmount());
        }
        recalculateAmounts(invoice, subtotal, taxTotal);
    }

    private void recalculateAmounts(Invoice invoice, BigDecimal lineSubtotal, BigDecimal lineTax) {
        BigDecimal subtotal = invoice.isPackagePriceEnabled()
                ? (invoice.getPackagePriceAmount() != null ? invoice.getPackagePriceAmount() : BigDecimal.ZERO)
                : lineSubtotal;
        if (invoice.isPackagePriceEnabled() && lineSubtotal.signum() > 0) {
            lineTax = lineTax.multiply(subtotal).divide(lineSubtotal, 2, RoundingMode.HALF_UP);
        } else if (invoice.isPackagePriceEnabled()) {
            lineTax = BigDecimal.ZERO;
        }
        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal discountAmount = DocumentDiscount.calculateAmount(
                subtotal, invoice.getDocumentDiscountType(), invoice.getDocumentDiscountValue());
        BigDecimal adjustedTax = DocumentDiscount.calculateTaxAfterDiscount(subtotal, lineTax, discountAmount);
        invoice.setSubtotal(subtotal);
        invoice.setDocumentDiscountAmount(discountAmount);
        invoice.setTaxAmount(adjustedTax);
        invoice.setTotalAmount(subtotal.subtract(discountAmount).add(adjustedTax).setScale(2, RoundingMode.HALF_UP));
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

        // Auto-post customer payment to General Ledger
        try {
            accountingService.postCustomerPayment(
                invoice.getId(),
                "INV-PAY-" + invoice.getInvoiceNumber(),
                LocalDate.now(),
                paymentAmount,
                true // to Bank
            );
        } catch (Exception e) {
            log.error("Failed to auto-post payment for Invoice #{}: {}", invoice.getInvoiceNumber(), e.getMessage());
        }

        AuditLogService.log(
                "PAYMENT",
                "INVOICE",
                invoice.getId(),
                "Recorded payment of " + paymentAmount + " for Invoice #" + invoice.getInvoiceNumber()
        );
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