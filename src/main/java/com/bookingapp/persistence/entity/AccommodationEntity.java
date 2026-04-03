package com.bookingapp.persistence.entity;

import com.bookingapp.domain.model.enums.AccommodationType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Entity
@Table(name = "accommodations")
@Data
public class AccommodationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private AccommodationType type;

    @Column(name = "location", nullable = false)
    private String location;

    @Column(name = "size", nullable = false)
    private String size;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "accommodation_amenities",
            joinColumns = @JoinColumn(name = "accommodation_id")
    )
    @OrderColumn(name = "amenity_order")
    @Column(name = "amenity", nullable = false)
    private List<String> amenities = new ArrayList<>();

    @Column(name = "daily_rate", nullable = false, precision = 12, scale = 2)
    private BigDecimal dailyRate;

    @Column(name = "availability", nullable = false)
    private Integer availability;
}
