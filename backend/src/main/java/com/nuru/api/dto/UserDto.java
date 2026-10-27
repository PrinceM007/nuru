package com.nuru.api.dto;

import com.nuru.users.User;
import com.nuru.users.UserConsent;
import com.nuru.users.UserPreference;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.UUID;

public final class UserDto {

  private UserDto() {
  }

  public record Profile(UUID id, String email, String displayName, String phone,
      OffsetDateTime createdAt) {
    public static Profile from(User user) {
      return new Profile(user.getId(), user.getEmail(), user.getDisplayName(),
          user.getPhone(), user.getCreatedAt());
    }
  }

  public record UpdateProfileRequest(
      @Size(min = 1, max = 120) String displayName,
      @Size(max = 40) String phone) {
  }

  public record Preferences(boolean emailEnabled, boolean smsEnabled, String locale) {
    public static Preferences from(UserPreference pref) {
      return new Preferences(pref.isEmailEnabled(), pref.isSmsEnabled(), pref.getLocale());
    }
  }

  public record UpdatePreferencesRequest(Boolean emailEnabled, Boolean smsEnabled,
      @Size(min = 2, max = 10) String locale) {
  }

  public record ConsentRequest(@NotBlank String purpose, @NotNull Boolean granted) {
  }

  public record Consent(String purpose, boolean granted, OffsetDateTime recordedAt) {
    public static Consent from(UserConsent consent) {
      return new Consent(consent.getPurpose(), consent.isGranted(), consent.getRecordedAt());
    }
  }
}
