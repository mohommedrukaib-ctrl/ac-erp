package com.aceerp.service;

import com.aceerp.model.SearchResult;
import com.aceerp.repository.SearchRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

/**
 * Service layer for universal search.
 * 
 * WHY A SERVICE LAYER ON TOP OF THE REPOSITORY?
 * The repository deals with raw database operations.
 * The service layer adds:
 *   - Input validation (is the query too short? too long?)
 *   - Business rules (should we sanitize the input?)
 *   - Error handling (return empty results instead of crashing)
 *   - Potential caching in the future
 * 
 * The controller (UI) calls the service, never the repository directly.
 * Controller → Service → Repository → Database
 */
public class SearchService {

    private static final Logger logger = LoggerFactory.getLogger(SearchService.class);

    private final SearchRepository searchRepository;

    // Minimum query length to start searching
    private static final int MIN_QUERY_LENGTH = 2;

    // Maximum query length (prevent abuse)
    private static final int MAX_QUERY_LENGTH = 100;

    public SearchService() {
        this.searchRepository = new SearchRepository();
    }

    /**
     * Perform a universal search across all module categories.
     * 
     * @param queryText the user's search input
     * @return list of search results, never null
     */
    public List<SearchResult> search(String queryText) {
        // Validate input
        if (queryText == null || queryText.trim().length() < MIN_QUERY_LENGTH) {
            return Collections.emptyList();
        }

        // Sanitize: trim and limit length
        String sanitized = queryText.trim();
        if (sanitized.length() > MAX_QUERY_LENGTH) {
            sanitized = sanitized.substring(0, MAX_QUERY_LENGTH);
        }

        try {
            List<SearchResult> results = searchRepository.searchAll(sanitized);
            logger.debug("Search for '{}' returned {} results.", sanitized, results.size());
            return results;
        } catch (Exception e) {
            logger.error("Search failed for query '{}': {}", sanitized, e.getMessage());
            return Collections.emptyList();
        }
    }
}