package com.telusko.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import com.telusko.model.SupportMessage;
import com.telusko.model.SupportTicket;

import java.util.List;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, Long> {

    List<SupportMessage> findByTicketOrderByCreatedAtAsc(SupportTicket ticket);
}
