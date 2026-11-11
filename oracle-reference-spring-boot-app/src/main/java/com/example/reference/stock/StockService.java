package com.example.reference.stock;

import com.example.reference.book.BookRepository;
import com.example.reference.book.CatalogItemNotFound;
import com.example.reference.spatial.BookstoreService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class StockService {
    private final StockRepository stocks;
    private final JdbcClient jdbc;
    private final BookRepository books;
    private final BookstoreService stores;

    public StockService(StockRepository stocks, JdbcClient jdbc, BookRepository books, BookstoreService stores) {
        this.stocks = stocks;
        this.jdbc = jdbc;
        this.books = books;
        this.stores = stores;
    }

    public record StockView(long id, long storeId, long bookId, int quantity, long version) {
        static StockView from(Stock stock) {
            return new StockView(stock.getId(), stock.getStoreId(), stock.getBookId(), stock.getQuantity(), stock.getVersion());
        }
    }

    public StockView get(long storeId, long bookId) {
        return StockView.from(find(storeId, bookId));
    }

    @Transactional
    public StockView create(long storeId, long bookId, int quantity) {
        if (!books.existsById(bookId)) {
            throw new CatalogItemNotFound("Book", bookId);
        }
        if (stores.find(storeId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Store not found: " + storeId);
        }
        return StockView.from(stocks.save(new Stock(storeId, bookId, quantity)));
    }

    @Transactional
    public StockView adjust(long storeId, long bookId, int delta, long expectedVersion) {
        Stock stock = find(storeId, bookId);
        if (stock.getVersion() != expectedVersion) {
            throw new InventoryConflict("Stock has changed. Reload it before adjusting quantity.");
        }
        stock.adjust(delta);
        stocks.flush();
        // JpaTransactionManager shares the connection with JdbcClient; both writes commit or roll back together.
        jdbc.sql("""
                insert into stock_adjustments (stock_id, delta, resulting_quantity)
                values (:stockId, :delta, :quantity)
                """).param("stockId", stock.getId()).param("delta", delta).param("quantity", stock.getQuantity()).update();
        return StockView.from(stock);
    }

    public record AdjustmentView(long id, int delta, int resultingQuantity, OffsetDateTime recordedAt) { }

    public List<AdjustmentView> history(long storeId, long bookId, int limit) {
        Stock stock = find(storeId, bookId);
        return jdbc.sql("""
                select id, delta, resulting_quantity, recorded_at
                from stock_adjustments
                where stock_id = :stockId
                order by id desc
                fetch first :limit rows only
                """).param("stockId", stock.getId()).param("limit", limit)
                .query((rs, row) -> new AdjustmentView(rs.getLong("id"), rs.getInt("delta"),
                        rs.getInt("resulting_quantity"), rs.getObject("recorded_at", OffsetDateTime.class))).list();
    }

    private Stock find(long storeId, long bookId) {
        return stocks.findByStoreIdAndBookId(storeId, bookId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No stock entry for this store and book"));
    }
}
