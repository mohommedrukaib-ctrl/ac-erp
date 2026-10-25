package com.aceerp.service;

import com.aceerp.model.Customer;
import com.aceerp.model.UserSession;
import com.aceerp.repository.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);
    private final CustomerRepository repo = new CustomerRepository();

    public List<Customer> listAll() {
        return repo.findAll();
    }

    public List<Customer> getAllCustomers() {
        return listAll();
    }

    public Optional<Customer> getById(int id) {
        return repo.findById(id);
    }

    public int addCustomer(String name, String phone, String email, String address, 
                           String city, String taxNumber, String notes) throws SQLException {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Customer name is required.");
        }

        Customer c = new Customer();
        c.setCustomerCode(repo.generateNextCode());
        c.setName(name.trim());
        c.setPhone(phone);
        c.setEmail(email);
        c.setAddress(address);
        c.setCity(city);
        c.setTaxNumber(taxNumber);
        c.setNotes(notes);
        c.setBalance(BigDecimal.ZERO);
        c.setActive(true);

        Integer userId = getCurrentUserId();
        return repo.insert(c, userId);
    }

    public void updateCustomer(Customer c) throws SQLException {
        if (c.getName() == null || c.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Customer name is required.");
        }
        Integer userId = getCurrentUserId();
        repo.update(c, userId);
    }

    public void deactivate(int id) throws SQLException {
        Integer userId = getCurrentUserId();
        repo.deactivate(id, userId);
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