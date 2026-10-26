package com.aceerp.service;

import com.aceerp.model.GoodsReceivedNote;
import com.aceerp.repository.GoodsReceivedNoteRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class GoodsReceivedNoteService {

    private final GoodsReceivedNoteRepository repository = new GoodsReceivedNoteRepository();

    public List<GoodsReceivedNote> getAllGrns() {
        return repository.findAll();
    }

    public GoodsReceivedNote getById(int id) {
        return repository.findById(id);
    }

    public GoodsReceivedNote receiveStock(GoodsReceivedNote grn) {
        if (grn.getGrnNumber() == null || grn.getGrnNumber().isBlank()) {
            grn.setGrnNumber(generateNextGrnNumber());
        }
        if (grn.getStatus() == null || grn.getStatus().isBlank()) {
            grn.setStatus("COMPLETED");
        }
        return repository.saveAndPostStock(grn);
    }

    public String generateNextGrnNumber() {
        String dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        long suffix = System.currentTimeMillis() % 10000;
        return String.format("GRN-%s-%04d", dateStr, suffix);
    }
}