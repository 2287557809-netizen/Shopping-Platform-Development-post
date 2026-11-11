package hu.unimiskolc.shopstream.search;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SearchIndexInitializerTest {

    @Mock
    private ElasticsearchOperations operations;

    @Mock
    private IndexOperations indexOperations;

    @Test
    void createsTheIndexWhenItDoesNotExist() {
        when(operations.indexOps(ProductDocument.class)).thenReturn(indexOperations);
        when(indexOperations.exists()).thenReturn(false);

        new SearchIndexInitializer(operations).run(null);

        verify(indexOperations).createWithMapping();
    }

    @Test
    void leavesAnExistingIndexAlone() {
        when(operations.indexOps(ProductDocument.class)).thenReturn(indexOperations);
        when(indexOperations.exists()).thenReturn(true);

        new SearchIndexInitializer(operations).run(null);

        verify(indexOperations, never()).createWithMapping();
    }
}
