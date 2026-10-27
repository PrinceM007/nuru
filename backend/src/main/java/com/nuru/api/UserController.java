package com.nuru.api;

import com.nuru.api.dto.UserDto;
import com.nuru.auth.JwtAuthenticationFilter;
import com.nuru.service.UserService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users")
public class UserController {

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @GetMapping("/me")
  public UserDto.Profile me() {
    return userService.getProfile(currentUser());
  }

  @PatchMapping("/me")
  public UserDto.Profile updateMe(@Valid @RequestBody UserDto.UpdateProfileRequest request) {
    return userService.updateProfile(currentUser(), request.displayName(), request.phone());
  }

  @GetMapping("/me/preferences")
  public UserDto.Preferences preferences() {
    return userService.getPreferences(currentUser());
  }

  @PatchMapping("/me/preferences")
  public UserDto.Preferences updatePreferences(
      @Valid @RequestBody UserDto.UpdatePreferencesRequest request) {
    return userService.updatePreferences(currentUser(), request.emailEnabled(),
        request.smsEnabled(), request.locale());
  }

  @PostMapping("/me/consents")
  public UserDto.Consent recordConsent(@Valid @RequestBody UserDto.ConsentRequest request) {
    return userService.recordConsent(currentUser(), request.purpose(), request.granted());
  }

  @GetMapping("/me/consents")
  public Map<String, UserDto.Consent> consents() {
    return userService.currentConsents(currentUser());
  }

  private UUID currentUser() {
    return JwtAuthenticationFilter.principal();
  }
}
