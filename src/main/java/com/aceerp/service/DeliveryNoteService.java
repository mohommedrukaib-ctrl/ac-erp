package com.aceerp.service;

import com.aceerp.model.DeliveryNote;
import com.aceerp.repository.DeliveryNoteRepository;

import java.sql.SQLException;
import java.util.List;

public class DeliveryNoteService {
    private final DeliveryNoteRepository dnRepository = new DeliveryNoteRepository();

    public List<DeliveryNote> getAllDeliveryNotes() {
        return dnRepository.findAll();
    }

    public DeliveryNote save(DeliveryNote dn) throws SQLException {
        return dnRepository.save(dn);
    }

    public void delete(int id) throws SQLException {
        dnRepository.delete(id);
    }

    public String generateNextDNNumber() {
        return dnRepository.generateNextDNNumber();
    }
}