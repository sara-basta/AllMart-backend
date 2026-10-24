package com.sara.allmart.controller;

import com.sara.allmart.entity.User;
import com.sara.allmart.repository.UserRepository;
import com.sara.allmart.service.FileUploadService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/upload")
public class FileUploadController {
    private final FileUploadService fileUploadService;
    private final UserRepository userRepository;

    public FileUploadController(FileUploadService fileUploadService, UserRepository userRepository) {
        this.fileUploadService = fileUploadService;
        this.userRepository = userRepository;
    }

    @PreAuthorize("hasAnyAuthority('SELLER', 'ADMIN')")
    @PostMapping
    public ResponseEntity<String> uploadFile(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserDetails user
    ) throws IOException {
        User authenticatedUser = userRepository.findByEmail(user.getUsername())
                .orElseThrow(() -> new IllegalArgumentException("Authenticated user not found."));

        String response = fileUploadService.uploadFile(file, authenticatedUser.getId());
        return ResponseEntity.ok(response);
    }
}
