package hu.unimiskolc.shopstream.search;

import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.json.JsonData;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

/**
 * Step 3, task 5: two ways of querying are used on purpose.
 *
 * <ul>
 *   <li>a single criterion (category only) goes through the derived
 *       repository method {@link ProductSearchRepository#findByCategory};</li>
 *   <li>the combined query (text + category + price range + paging + sorting)
 *       is built as a NativeQuery and executed with
 *       {@link ElasticsearchOperations}, because a method name cannot express a
 *       bool query with must/filter parts.</li>
 * </ul>
 */
@Service
public class SearchService {

    private final ProductSearchRepository repository;
    private final ElasticsearchOperations operations;

    public SearchService(ProductSearchRepository repository, ElasticsearchOperations operations) {
        this.repository = repository;
        this.operations = operations;
    }

    public record SearchPage(
            List<ProductDocument> content,
            long totalElements,
            int page,
            int size,
            int totalPages) {
    }

    public SearchPage search(String text,
                             String category,
                             BigDecimal minPrice,
                             BigDecimal maxPrice,
                             int page,
                             int size,
                             String sort) {

        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        boolean hasText = text != null && !text.isBlank();
        boolean hasCategory = category != null && !category.isBlank();
        boolean hasPrice = minPrice != null || maxPrice != null;
        Sort sortSpec = toSort(sort);

        if (!hasText && !hasPrice && hasCategory && sortSpec.isUnsorted()) {
            Page<ProductDocument> result = repository.findByCategory(
                    category.trim(), PageRequest.of(safePage, safeSize));
            return toPage(result.getContent(), result.getTotalElements(), safePage, safeSize);
        }

        BoolQuery.Builder bool = new BoolQuery.Builder();

        if (hasText) {
            // Both words must appear in one field, so "laptop 14" does not match
            // every product that contains either word.
            bool.must(m -> m.multiMatch(mm -> mm
                    .query(text.trim())
                    .fields("name^2", "description")
                    .operator(Operator.And)));
        }

        if (hasCategory) {
            bool.filter(f -> f.term(t -> t.field("category").value(category.trim())));
        }

        if (hasPrice) {
            bool.filter(f -> f.range(r -> {
                r.field("price");
                if (minPrice != null) {
                    r.gte(JsonData.of(minPrice.doubleValue()));
                }
                if (maxPrice != null) {
                    r.lte(JsonData.of(maxPrice.doubleValue()));
                }
                return r;
            }));
        }

        NativeQueryBuilder builder = NativeQuery.builder()
                .withQuery(q -> q.bool(bool.build()))
                .withPageable(PageRequest.of(safePage, safeSize))
                .withTrackTotalHits(true);
        if (sortSpec.isSorted()) {
            builder.withSort(sortSpec);
        }

        SearchHits<ProductDocument> hits = operations.search(builder.build(), ProductDocument.class);
        List<ProductDocument> content = hits.getSearchHits().stream()
                .map(SearchHit::getContent)
                .toList();

        return toPage(content, hits.getTotalHits(), safePage, safeSize);
    }

    private SearchPage toPage(List<ProductDocument> content, long total, int page, int size) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) total / size);
        return new SearchPage(content, total, page, size, totalPages);
    }

    private Sort toSort(String sort) {
        if (sort == null) {
            return Sort.unsorted();
        }
        return switch (sort) {
            case "priceAsc" -> Sort.by(Sort.Order.asc("price"));
            case "priceDesc" -> Sort.by(Sort.Order.desc("price"));
            case "nameAsc" -> Sort.by(Sort.Order.asc("name.keyword"));
            default -> Sort.unsorted();
        };
    }
}
