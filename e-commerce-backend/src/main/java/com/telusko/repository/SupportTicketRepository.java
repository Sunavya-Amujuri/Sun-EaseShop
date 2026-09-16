package com.telusko.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import com.telusko.enums.TicketStatus;
import com.telusko.model.SupportTicket;
import com.telusko.model.User;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    Page<SupportTicket> findByUser(User user, Pageable pageable);

    Page<SupportTicket> findByStatus(TicketStatus status, Pageable pageable);
}

