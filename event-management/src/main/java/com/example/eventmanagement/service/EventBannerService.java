package com.example.eventmanagement.service;

import com.example.eventmanagement.model.Event;
import com.example.eventmanagement.repository.EventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;

/**
 * Service tự động thêm banner image cho events chưa có hình
 */
@Service
public class EventBannerService {

    private static final Logger logger = LoggerFactory.getLogger(EventBannerService.class);

    @Autowired
    private EventRepository eventRepository;

    @Value("${app.upload.dir}")
    private String uploadDir;

    private final RestTemplate restTemplate;

    // Unsplash access key (free tier, có thể không cần key cho unspash API)
    private static final String UNSPLASH_API = "https://source.unsplash.com/800x600/?";

    // Mapping: từ event title -> search keywords
    private static final Map<String, String> EVENT_KEYWORDS = new HashMap<>();

    static {
        EVENT_KEYWORDS.put("concert", "music,concert,stage,performance");
        EVENT_KEYWORDS.put("workshop", "workshop,learning,education,people,training");
        EVENT_KEYWORDS.put("conference", "conference,business,presentation,meeting");
        EVENT_KEYWORDS.put("festival", "festival,celebration,crowd,fun,party");
        EVENT_KEYWORDS.put("sports", "sports,action,fitness,achievement");
        EVENT_KEYWORDS.put("art", "art,gallery,creative,paintings,museum");
        EVENT_KEYWORDS.put("food", "food,restaurant,cooking,dishes,cuisine");
        EVENT_KEYWORDS.put("tech", "technology,computer,innovation,coding,tech");
        EVENT_KEYWORDS.put("movie", "cinema,film,movie,entertainment,screen");
        EVENT_KEYWORDS.put("dance", "dance,movement,performance,music,celebration");
        EVENT_KEYWORDS.put("yoga", "yoga,meditation,wellness,health,relaxation");
        EVENT_KEYWORDS.put("travel", "travel,adventure,journey,destination,exploration");
        EVENT_KEYWORDS.put("book", "books,reading,library,literature,knowledge");
        EVENT_KEYWORDS.put("game", "games,gaming,fun,entertainment,competition");
        EVENT_KEYWORDS.put("photo", "photography,camera,pictures,visual,art");
    }

    public EventBannerService(RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    /**
     * Tìm keyword phù hợp từ event title
     */
    private String findKeywordFromTitle(String title) {
        if (title == null) {
            return "event,celebration,people,gathering";
        }

        String lowerTitle = title.toLowerCase();
        
        for (String keyword : EVENT_KEYWORDS.keySet()) {
            if (lowerTitle.contains(keyword)) {
                return EVENT_KEYWORDS.get(keyword);
            }
        }
        
        // Default: tìm từ title
        return lowerTitle.substring(0, Math.min(3, lowerTitle.length())) + ",event";
    }

    /**
     * Download ảnh từ Unsplash và lưu vào thư mục
     */
    public String downloadAndSaveImage(String searchKeyword, String eventId) {
        try {
            // Tạo thư mục nếu chưa có
            File uploadDirFile = new File(uploadDir);
            if (!uploadDirFile.exists()) {
                uploadDirFile.mkdirs();
            }

            // Unsplash API URL (mỗi request trả ảnh random khác nhau)
            String imageUrl = UNSPLASH_API + searchKeyword.replace(",", "+");
            
            logger.info("[EventBannerService] Downloading image for event={} from URL={}", eventId, imageUrl);

            // Download ảnh
            URL url = new URL(imageUrl);
            InputStream input = url.openStream();
            
            // Tạo filename duy nhất
            String fileName = UUID.randomUUID() + ".jpg";
            String filePath = uploadDir + fileName;
            
            // Lưu vào file
            FileOutputStream output = new FileOutputStream(filePath);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
            output.close();
            input.close();
            
            logger.info("[EventBannerService] Saved image for event={} to file={}", eventId, filePath);
            
            // Trả về relative path (để lưu vào database)
            return "uploads/" + fileName;
            
        } catch (IOException e) {
            logger.error("[EventBannerService] Error downloading image for event=" + eventId, e);
            return null;
        }
    }

    /**
     * Tự động thêm banner cho tất cả events chưa có hình
     */
    public void updateMissingBanners() {
        try {
            logger.info("[EventBannerService] Starting to update missing banners...");
            
            List<Event> allEvents = eventRepository.findAll();
            int updated = 0;
            
            for (Event event : allEvents) {
                // Bỏ qua event đã có hình
                if (event.getBannerImagePath() != null && !event.getBannerImagePath().isEmpty()) {
                    continue;
                }
                
                logger.info("[EventBannerService] Processing event: {}", event.getTitle());
                
                // Tìm keyword phù hợp
                String keyword = findKeywordFromTitle(event.getTitle());
                
                // Download và lưu ảnh
                String imagePath = downloadAndSaveImage(keyword, event.getId());
                
                if (imagePath != null) {
                    event.setBannerImagePath(imagePath);
                    eventRepository.save(event);
                    updated++;
                    logger.info("[EventBannerService] Updated event={} with banner={}", event.getTitle(), imagePath);
                    
                    // Delay 1 giây giữa các request để tránh rate limit
                    Thread.sleep(1000);
                } else {
                    logger.warn("[EventBannerService] Failed to update event={}", event.getTitle());
                }
            }
            
            logger.info("[EventBannerService] Completed! Updated {} events with banners", updated);
            
        } catch (InterruptedException e) {
            logger.error("[EventBannerService] Interrupted while updating banners", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logger.error("[EventBannerService] Error during banner update", e);
        }
    }
}
