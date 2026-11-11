package hu.unimiskolc.shopstream.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/search")
public class SearchController {

    private final ElasticsearchClient client;

    public SearchController(ElasticsearchClient client) {
        this.client = client;
    }

    public record SearchPage(
            List<ProductDocument> content,
            long totalElements,
            int page,
            int size,
            int totalPages) {
    }

    @GetMapping
    public SearchPage search(
            @RequestParam(required = false) String text,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String sort) {

        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));

        BoolQuery.Builder bool = new BoolQuery.Builder();

        if (text != null && !text.isBlank()) {
            // Every token has to match the same field: a product search for
            // "smoke keyboard 42" must not hit every "smoke ... 42" row.
            bool.must(m -> m.multiMatch(mm -> mm
                    .query(text.trim())
                    .fields("name^2", "description")
                    .operator(Operator.And)));
        }

        if (category != null && !category.isBlank()) {
            bool.filter(f -> f.term(t -> t.field("category").value(category.trim())));
        }

        if (minPrice != null || maxPrice != null) {
            bool.filter(f -> f.range(r -> {
                r.field("price");
                if (minPrice != null) {
                    r.gte(JsonData.of(minPrice));
                }
                if (maxPrice != null) {
                    r.lte(JsonData.of(maxPrice));
                }
                return r;
            }));
        }

        try {
            SearchResponse<ProductDocument> response = client.search(s -> {
                s.index("products")
                        .from(safePage * safeSize)
                        .size(safeSize)
                        .trackTotalHits(t -> t.enabled(true))
                        .query(q -> q.bool(bool.build()));

                switch (sort == null ? "" : sort) {
                    case "priceAsc" -> s.sort(so -> so.field(f -> f.field("price").order(SortOrder.Asc)));
                    case "priceDesc" -> s.sort(so -> so.field(f -> f.field("price").order(SortOrder.Desc)));
                    case "nameAsc" -> s.sort(so -> so.field(f -> f.field("name.keyword").order(SortOrder.Asc)));
                    default -> {
                        // Elasticsearch relevance order
                    }
                }
                return s;
            }, ProductDocument.class);

            List<ProductDocument> content = response.hits().hits().stream()
                    .map(Hit::source)
                    .toList();
            long total = response.hits().total() == null
                    ? content.size()
                    : response.hits().total().value();
            int totalPages = safeSize == 0 ? 0 : (int) Math.ceil((double) total / safeSize);
            return new SearchPage(content, total, safePage, safeSize, totalPages);
        } catch (IOException ex) {
            throw new SearchUnavailableException("Elasticsearch query failed", ex);
        }
    }
}
