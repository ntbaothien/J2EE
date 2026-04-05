package com.example.eventmanagement.repository;

import com.example.eventmanagement.model.Booking;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface BookingRepository extends MongoRepository<Booking, String> {
    List<Booking> findByUserId(String userId);
    List<Booking> findByEventId(String eventId);
    List<Booking> findByEventIdAndStatus(String eventId, String status);
    List<Booking> findByStatus(String status);
    long countByEventIdAndStatus(String eventId, String status);
    boolean existsByEventIdAndUserIdAndStatus(String eventId, String userId, String status);
}
