package com.example.support;

import java.util.List;

import com.example.support.model.ImpactPath;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class TicketImpactService {
    private static final String IMPACT_SQL = """
            select customer_name,
                   customer_tier,
                   order_id,
                   order_status,
                   product_name
            from graph_table (support_ticket_graph
                match
                (ticket is ticket where ticket.ticket_id = ?)
                    -[affects is affects]->
                (product is product)
                    <-[bought is bought]-
                (customer is customer)
                columns (
                    customer.name as customer_name,
                    customer.tier as customer_tier,
                    bought.order_id as order_id,
                    bought.order_status as order_status,
                    product.name as product_name
                )
            )
            order by customer_name, order_id
            """;

    private final JdbcTemplate jdbcTemplate;

    TicketImpactService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<ImpactPath> findImpact(long ticketId) {
        return jdbcTemplate.query(
                IMPACT_SQL,
                (resultSet, rowNum) -> new ImpactPath(
                        resultSet.getString("customer_name"),
                        resultSet.getString("customer_tier"),
                        resultSet.getLong("order_id"),
                        resultSet.getString("order_status"),
                        resultSet.getString("product_name")
                ),
                ticketId
        );
    }
}
