package com.nuru.api;

import com.nuru.api.dto.NotificationDto;
import com.nuru.auth.JwtAuthenticationFilter;
import com.nuru.service.GroupAuth;
import com.nuru.service.NotificationService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Notifications")
public class NotificationController {

  private final NotificationService notificationService;
  private final GroupAuth groupAuth;

  public NotificationController(NotificationService notificationService, GroupAuth groupAuth) {
    this.notificationService = notificationService;
    this.groupAuth = groupAuth;
  }

  @GetMapping("/api/v1/notifications/me")
  public Page<NotificationDto.Response> mine(@PageableDefault(size = 20) Pageable pageable) {
    return notificationService.myNotifications(currentUser(), pageable)
        .map(NotificationDto.Response::from);
  }

  @GetMapping("/api/v1/groups/{groupId}/notifications")
  public Page<NotificationDto.Response> forGroup(@PathVariable UUID groupId,
      @PageableDefault(size = 20) Pageable pageable) {
    return notificationService.groupNotifications(groupId, currentUser(), groupAuth, pageable)
        .map(NotificationDto.Response::from);
  }

  private UUID currentUser() {
    return JwtAuthenticationFilter.principal();
  }
}
