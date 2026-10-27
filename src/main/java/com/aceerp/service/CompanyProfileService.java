package com.aceerp.service;

import com.aceerp.config.DatabaseConfig;
import com.aceerp.model.CompanyProfile;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.*;
import java.sql.*;
import java.util.UUID;

public class CompanyProfileService {

    private static final Logger logger = LoggerFactory.getLogger(CompanyProfileService.class);
    private static final Path LOGO_DIR = Paths.get("config", "branding");

    public CompanyProfile getProfile() {
        String sql = "SELECT * FROM company_profile WHERE id = 1";
        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return map(rs);
            }
        } catch (SQLException e) {
            logger.error("Failed to load company profile: {}", e.getMessage(), e);
        }
        return new CompanyProfile(); // empty — user fills in Settings
    }

    public boolean saveProfile(CompanyProfile p) {
        String sql = """
            INSERT INTO company_profile (
                id, company_name, tagline, address_line1, address_line2, city, state, postal_code,
                country, phone, email, website, tax_number, logo_path, invoice_footer,
                default_print_template, updated_at
            ) VALUES (
                1, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP
            )
            ON CONFLICT (id) DO UPDATE SET
                company_name = EXCLUDED.company_name,
                tagline = EXCLUDED.tagline,
                address_line1 = EXCLUDED.address_line1,
                address_line2 = EXCLUDED.address_line2,
                city = EXCLUDED.city,
                state = EXCLUDED.state,
                postal_code = EXCLUDED.postal_code,
                country = EXCLUDED.country,
                phone = EXCLUDED.phone,
                email = EXCLUDED.email,
                website = EXCLUDED.website,
                tax_number = EXCLUDED.tax_number,
                logo_path = EXCLUDED.logo_path,
                invoice_footer = EXCLUDED.invoice_footer,
                default_print_template = EXCLUDED.default_print_template,
                updated_at = CURRENT_TIMESTAMP
            """;

        try (Connection conn = DatabaseConfig.getInstance().getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, blankToNull(p.getCompanyName()));
            stmt.setString(2, blankToNull(p.getTagline()));
            stmt.setString(3, blankToNull(p.getAddressLine1()));
            stmt.setString(4, blankToNull(p.getAddressLine2()));
            stmt.setString(5, blankToNull(p.getCity()));
            stmt.setString(6, blankToNull(p.getState()));
            stmt.setString(7, blankToNull(p.getPostalCode()));
            stmt.setString(8, blankToNull(p.getCountry()));
            stmt.setString(9, blankToNull(p.getPhone()));
            stmt.setString(10, blankToNull(p.getEmail()));
            stmt.setString(11, blankToNull(p.getWebsite()));
            stmt.setString(12, blankToNull(p.getTaxNumber()));
            stmt.setString(13, blankToNull(p.getLogoPath()));
            stmt.setString(14, blankToNull(p.getInvoiceFooter()));
            stmt.setString(15, p.getDefaultPrintTemplate() != null ? p.getDefaultPrintTemplate() : "CLASSIC");
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            logger.error("Failed to save company profile: {}", e.getMessage(), e);
            return false;
        }
    }

    public String uploadLogo(Path sourceFile) throws IOException {
        if (sourceFile == null || !Files.exists(sourceFile)) {
            throw new IOException("Logo file not found.");
        }
        String name = sourceFile.getFileName().toString().toLowerCase();
        if (!(name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".gif") || name.endsWith(".webp"))) {
            throw new IOException("Unsupported format. Use PNG or JPG.");
        }

        Files.createDirectories(LOGO_DIR);
        String ext = name.substring(name.lastIndexOf('.'));
        String stored = "company_logo_" + UUID.randomUUID().toString().substring(0, 8) + ext;
        Path target = LOGO_DIR.resolve(stored);
        Files.copy(sourceFile, target, StandardCopyOption.REPLACE_EXISTING);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(LOGO_DIR, "company_logo_*")) {
            for (Path old : stream) {
                if (!old.equals(target)) {
                    try { Files.deleteIfExists(old); } catch (Exception ignored) {}
                }
            }
        }
        return target.toString().replace('\\', '/');
    }

    public String resolveLogoAbsolutePath(String logoPath) {
        if (logoPath == null || logoPath.isBlank()) return null;
        Path p = Paths.get(logoPath);
        if (!p.isAbsolute()) {
            p = Paths.get(System.getProperty("user.dir")).resolve(logoPath).normalize();
        }
        return Files.exists(p) ? p.toAbsolutePath().toString().replace('\\', '/') : null;
    }

    private CompanyProfile map(ResultSet rs) throws SQLException {
        CompanyProfile p = new CompanyProfile();
        p.setId(rs.getInt("id"));
        p.setCompanyName(rs.getString("company_name"));
        p.setTagline(rs.getString("tagline"));
        p.setAddressLine1(rs.getString("address_line1"));
        p.setAddressLine2(rs.getString("address_line2"));
        p.setCity(rs.getString("city"));
        p.setState(rs.getString("state"));
        p.setPostalCode(rs.getString("postal_code"));
        p.setCountry(rs.getString("country"));
        p.setPhone(rs.getString("phone"));
        p.setEmail(rs.getString("email"));
        p.setWebsite(rs.getString("website"));
        p.setTaxNumber(rs.getString("tax_number"));
        p.setLogoPath(rs.getString("logo_path"));
        p.setInvoiceFooter(rs.getString("invoice_footer"));
        p.setDefaultPrintTemplate(rs.getString("default_print_template"));
        Timestamp ts = rs.getTimestamp("updated_at");
        if (ts != null) p.setUpdatedAt(ts.toLocalDateTime());
        return p;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}