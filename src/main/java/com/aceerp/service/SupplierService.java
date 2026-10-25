package com.aceerp.service;

import com.aceerp.model.Supplier;
import com.aceerp.model.UserSession;
import com.aceerp.repository.SupplierRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.List;

public class SupplierService {

    private static final Logger log = LoggerFactory.getLogger(SupplierService.class);
    private final SupplierRepository repo = new SupplierRepository();

    public List<Supplier> listAll() {
        return repo.findAll();
    }

    public void addSupplier(String name, String contactPerson, String phone, String email,
                            String address, String city, String taxNumber, String notes)
            throws SQLException {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Supplier name is required.");
        }
        Supplier s = new Supplier();
        s.setSupplierCode(repo.generateNextCode());
        s.setName(name.trim());
        s.setContactPerson(contactPerson != null ? contactPerson.trim() : null);
        s.setPhone(phone != null ? phone.trim() : null);
        s.setEmail(email != null ? email.trim() : null);
        s.setAddress(address != null ? address.trim() : null);
        s.setCity(city != null ? city.trim() : null);
        s.setTaxNumber(taxNumber != null ? taxNumber.trim() : null);
        s.setNotes(notes != null ? notes.trim() : null);

        repo.insert(s, getCurrentUserId());
        log.info("Supplier created: {}", s.getName());
    }

    public void updateSupplier(Supplier s) throws SQLException {
        if (s.getName() == null || s.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Supplier name is required.");
        }
        repo.update(s, getCurrentUserId());
        log.info("Supplier updated: {}", s.getName());
    }

    public void deactivate(int id) throws SQLException {
        repo.deactivate(id, getCurrentUserId());
        log.info("Supplier deactivated: id={}", id);
    }

    private Integer getCurrentUserId() {
        try {
            if (UserSession.getInstance().isLoggedIn()) {
                return UserSession.getInstance().getCurrentUser().getId();
            }
        } catch (Exception e) { /* ignore */ }
        return null;
    }
}   