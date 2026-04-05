package com.example.eventmanagement.service;

import com.example.eventmanagement.model.Booking;
import com.example.eventmanagement.model.Event;
import com.example.eventmanagement.model.Registration;
import com.example.eventmanagement.model.enums.BookingStatus;
import com.example.eventmanagement.model.enums.RegistrationStatus;
import com.example.eventmanagement.repository.BookingRepository;
import com.example.eventmanagement.repository.EventRepository;
import com.example.eventmanagement.repository.RegistrationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service để tự động hủy hiệu lực vé khi sự kiện kết thúc
 */
@Service
public class TicketExpiredService {

    private static final Logger logger = LoggerFactory.getLogger(TicketExpiredService.class);

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private RegistrationRepository registrationRepository;

    @Autowired
    private EventRepository eventRepository;

    /**
     * Chạy mỗi 5 phút (300,000 ms) để kiểm tra và mark các vé hết hạn
     * Cron: Chạy vào lúc 0, 5, 10, 15... phút mỗi giờ
     */
    @Scheduled(fixedRate = 300000)
    public void expireTicketsForCompletedEvents() {
        logger.info("[TicketExpiredService] Starting scheduled job to expire tickets...");
        
        LocalDateTime now = LocalDateTime.now();
        
        // --- Expire Bookings ---
        try {
            List<Booking> bookings = bookingRepository.findByStatus(BookingStatus.CONFIRMED.toString());
            List<Booking> toExpire = bookings.stream()
                .filter(b -> {
                    Event event = eventRepository.findById(b.getEventId()).orElse(null);
                    return event != null && event.getEndDate() != null && event.getEndDate().isBefore(now);
                })
                .collect(Collectors.toList());
            
            if (!toExpire.isEmpty()) {
                toExpire.forEach(b -> {
                    b.setStatus(BookingStatus.EXPIRED.toString());
                    b.setCancelledAt(now);
                });
                bookingRepository.saveAll(toExpire);
                logger.info("[TicketExpiredService] Expired {} bookings for completed events", toExpire.size());
            }
        } catch (Exception e) {
            logger.error("[TicketExpiredService] Error expiring bookings", e);
        }

        // --- Expire Registrations ---
        try {
            List<Registration> registrations = registrationRepository.findByStatus(RegistrationStatus.CONFIRMED.toString());
            List<Registration> toExpire = registrations.stream()
                .filter(r -> {
                    Event event = eventRepository.findById(r.getEventId()).orElse(null);
                    return event != null && event.getEndDate() != null && event.getEndDate().isBefore(now);
                })
                .collect(Collectors.toList());
            
            if (!toExpire.isEmpty()) {
                toExpire.forEach(r -> r.setStatus(RegistrationStatus.EXPIRED.toString()));
                registrationRepository.saveAll(toExpire);
                logger.info("[TicketExpiredService] Expired {} registrations for completed events", toExpire.size());
            }
        } catch (Exception e) {
            logger.error("[TicketExpiredService] Error expiring registrations", e);
        }
        
        logger.info("[TicketExpiredService] Scheduled job completed");
    }
}
