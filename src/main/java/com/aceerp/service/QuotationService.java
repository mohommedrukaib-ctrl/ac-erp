package com.aceerp.service;

import com.aceerp.model.Invoice;
import com.aceerp.model.InvoiceLine;
import com.aceerp.model.Quotation;
import com.aceerp.model.QuotationLine;
import com.aceerp.repository.QuotationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class QuotationService {

    private static final Logger log = LoggerFactory.getLogger(QuotationService.class);
    private final QuotationRepository quotationRepository = new QuotationRepository();

    public List<Quotation> listAll() {
        return quotationRepository.findAll();
    }

    public List<Quotation> getAllQuotations() {
        return listAll();
    }

    public Optional<Quotation> findById(int id) {
        return quotationRepository.findById(id);
    }

    public Quotation saveQuotation(Quotation quotation) {
        boolean isNew = quotation.getId() <= 0;
        if (quotation.getQuoteNumber() == null || quotation.getQuoteNumber().isBlank()) {
            quotation.setQuoteNumber(generateNextQuoteNumber());
        }
        quotation.recalculateTotals();
        Quotation saved = quotationRepository.save(quotation);

        AuditLogService.log(
                isNew ? "CREATE" : "UPDATE",
                "QUOTATION",
                saved.getId(),
                "Saved Quotation #" + saved.getQuoteNumber() + " Total: " + saved.getTotalAmount()
        );

        return saved;
    }

    public String generateNextQuoteNumber() {
        return "QT-" + (System.currentTimeMillis() / 1000);
    }

    public Invoice convertToInvoice(Quotation quote) {
        Invoice invoice = new Invoice();
        invoice.setCustomerId(quote.getCustomerId());
        invoice.setCustomerName(quote.getCustomerName());
        invoice.setInvoiceDate(LocalDate.now());
        invoice.setDueDate(LocalDate.now().plusDays(30));
        invoice.setStatus("UNPAID");
        invoice.setSubtotal(quote.getSubtotal());
        invoice.setTaxAmount(quote.getTaxAmount());
        invoice.setTotalAmount(quote.getTotalAmount());
        invoice.setNotes("Created from Quotation #" + quote.getQuoteNumber());

        List<InvoiceLine> invoiceLines = new ArrayList<>();
        for (QuotationLine qLine : quote.getLines()) {
            InvoiceLine iLine = new InvoiceLine();
            if (qLine.getProductId() != null) {
                iLine.setProductId(qLine.getProductId());
            }
            iLine.setDescription(qLine.getDescription());
            iLine.setQuantity(qLine.getQuantity());
            iLine.setUnitPrice(qLine.getUnitPrice());
            iLine.setTotalPrice(qLine.getTotalPrice());
            invoiceLines.add(iLine);
        }
        invoice.setLines(invoiceLines);

        AuditLogService.log(
                "CONVERT",
                "QUOTATION",
                quote.getId(),
                "Converted Quotation #" + quote.getQuoteNumber() + " to Invoice"
        );

        return invoice;
    }
}