package com.example.appsdeveloperblog.ws.emailnotification.handler;

import com.example.appsdeveloperblog.ws.core.ProductCreatedEvent;
import com.example.appsdeveloperblog.ws.emailnotification.error.NonRetryableException;
import com.example.appsdeveloperblog.ws.emailnotification.error.RetryableException;
import com.example.appsdeveloperblog.ws.emailnotification.io.ProcessedEventEntity;
import com.example.appsdeveloperblog.ws.emailnotification.io.ProcessedEventRepository;
import org.apache.kafka.common.protocol.types.Field;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

@Component
@KafkaListener(topics="product-created-events-topic")
public class ProductCreatedEventHandler {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private RestTemplate restTemplate;
    private ProcessedEventRepository processedEventRepository;
    public ProductCreatedEventHandler(RestTemplate restTemplate, ProcessedEventRepository processedEventRepository) {
        this.restTemplate = restTemplate;
        this.processedEventRepository = processedEventRepository;
    }
    @Transactional
    @KafkaHandler
    public void handle(@Payload ProductCreatedEvent productCreatedEvent, @Header("messageId") String messageId, @Header(KafkaHeaders.RECEIVED_KEY) String messageKey){
        logger.info("Received a new Event " + productCreatedEvent.getTitle() + " with product id"+ productCreatedEvent.getProductId());

        //check if message was already processed before
        ProcessedEventEntity existingRecord= processedEventRepository.findByMessageId(messageId);
        if(existingRecord!=null){
            logger.info("Found a duplicate message id: {}", existingRecord.getMessageId());
        }

        String requestURL = "http://localhost:8082/response/200";

        try {
            ResponseEntity<String> response = restTemplate.exchange(requestURL, HttpMethod.GET, null, String.class);
            if(response.getStatusCode().value()==HttpStatus.OK.value()){
                logger.info("Received response from remote service" + response.getBody());
            }
        }
        catch (ResourceAccessException ex){
            logger.error(ex.getMessage());
            throw new RetryableException(ex);
        }
        catch (HttpServerErrorException ex){
            logger.error(ex.getMessage());
            throw new NonRetryableException(ex);
        }
        catch (Exception ex){
            logger.error(ex.getMessage());
            throw new NonRetryableException(ex);
        }

        // save a unique message id in the database
        try {
            processedEventRepository.save(new ProcessedEventEntity(messageId, productCreatedEvent.getProductId()));
        }
        catch (DataIntegrityViolationException ex){
            throw new NonRetryableException(ex);
        }
    }
}
