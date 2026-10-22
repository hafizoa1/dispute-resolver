package com.hafiz.disputeresolver.dto;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;

// Not a JSON response DTO like the others - a transfer object between the service
// (which decides preview-vs-download and the safe media type) and the controller
// (which assembles the actual HTTP response from it).
public record EvidenceFile(Resource resource, MediaType mediaType, ContentDisposition contentDisposition) {
}
