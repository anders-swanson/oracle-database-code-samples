package com.example.reference.spatial;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class BookstoreService {
    private static final String ORIGIN = """
            mdsys.sdo_geometry(2001, 8307,
                mdsys.sdo_point_type(:longitude, :latitude, null), null, null)
            """;
    public static final String NEARBY_SQL = """
            select s.id as store_id,
                   s.name as store_name,
                   sdo_geom.sdo_distance(s.location, %s, 0.005, 'unit=KM') as distance_km
            from bookstores s
            where sdo_within_distance(s.location, %s, :radius) = 'TRUE'
            """.formatted(ORIGIN, ORIGIN);

    private final JdbcClient jdbc;

    public BookstoreService(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record Store(long id, String name, double longitude, double latitude) { }
    public record NearbyStore(long storeId, String storeName, double distanceKm) { }

    @Transactional
    public Store create(String name, double longitude, double latitude) {
        var keys = new GeneratedKeyHolder();
        jdbc.sql("insert into bookstores (name, location) values (:name, " + ORIGIN + ")")
                .param("name", name.strip()).param("longitude", longitude).param("latitude", latitude)
                .update(keys, "ID");
        return new Store(keys.getKey().longValue(), name.strip(), longitude, latitude);
    }

    public Optional<Store> find(long id) {
        return jdbc.sql("""
                select s.id,
                       s.name,
                       s.location.sdo_point.x as longitude,
                       s.location.sdo_point.y as latitude
                from bookstores s
                where s.id = :id
                """).param("id", id).query((rs, row) -> new Store(rs.getLong("id"), rs.getString("name"),
                        rs.getDouble("longitude"), rs.getDouble("latitude"))).optional();
    }

    public List<NearbyStore> nearby(double longitude, double latitude, double radiusKm) {
        return jdbc.sql(NEARBY_SQL + " order by distance_km, store_id fetch first 100 rows only")
                .param("longitude", longitude).param("latitude", latitude)
                .param("radius", radiusParameter(radiusKm))
                .query((rs, row) -> new NearbyStore(rs.getLong("store_id"), rs.getString("store_name"),
                        rs.getDouble("distance_km"))).list();
    }

    public static String radiusParameter(double radiusKm) {
        return "distance=" + Double.toString(radiusKm) + " unit=KM";
    }
}
