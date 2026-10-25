package com.aceerp.service;

import com.aceerp.model.Invoice;
import com.aceerp.model.InvoiceLine;
import com.aceerp.model.Quotation;
import com.aceerp.model.QuotationLine;
import com.aceerp.repository.QuotationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
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
        if (quotation.getQuoteNumber() == null || quotation.getQuoteNumber().isBlank()) {
            quotation.setQuoteNumber(generateNextQuoteNumber());
        }
        quotation.recalculateTotals();
        return quotationRepository.save(quotation);
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
        return invoice;
    }
}