package com.example.support;

import com.example.support.messaging.TicketEventProducer;
import com.example.support.model.ImpactResponse;
import com.example.support.model.SimilarIncidentResponse;
import com.example.support.model.TicketRequest;
import com.example.support.model.TicketResponse;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class TicketController {
    private final TicketEventProducer ticketEventProducer;
    private final TicketSearchService ticketSearchService;
    private final TicketImpactService ticketImpactService;

    TicketController(
            TicketEventProducer ticketEventProducer,
            TicketSearchService ticketSearchService,
            TicketImpactService ticketImpactService
    ) {
        this.ticketEventProducer = ticketEventProducer;
        this.ticketSearchService = ticketSearchService;
        this.ticketImpactService = ticketImpactService;
    }

    @PostMapping("/tickets")
    TicketResponse openTicket(@RequestBody TicketRequest request) {
        return ticketEventProducer.openTicket(request);
    }

    @GetMapping("/tickets/{ticketId}/similar")
    SimilarIncidentResponse similarIncidents(
            @PathVariable long ticketId,
            @RequestParam(defaultValue = "ENTERPRISE") String customerTier,
            @RequestParam(defaultValue = "OPEN") String slaStatus
    ) {
        return new SimilarIncidentResponse(ticketSearchService.findSimilarIncidents(ticketId, customerTier, slaStatus));
    }

    @GetMapping("/tickets/{ticketId}/impact")
    ImpactResponse impact(@PathVariable long ticketId) {
        return new ImpactResponse(ticketId, ticketImpactService.findImpact(ticketId));
    }

    @GetMapping(value = "/tickets/{ticketId}/document", produces = MediaType.APPLICATION_JSON_VALUE)
    ResponseEntity<String> document(@PathVariable long ticketId) {
        return ResponseEntity.ok(ticketSearchService.findTicketDocument(ticketId));
    }
}
