package com.nuru.service;

import com.nuru.api.dto.UserDto;
import com.nuru.api.error.ResourceNotFoundException;
import com.nuru.api.error.UnprocessableException;
import com.nuru.audit.AuditActions;
import com.nuru.audit.AuditService;
import com.nuru.users.User;
import com.nuru.users.UserConsent;
import com.nuru.users.UserConsentRepository;
import com.nuru.users.UserPreference;
import com.nuru.users.UserPreferenceRepository;
import com.nuru.users.UserRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  private static final Set<String> KNOWN_PURPOSES = Set.of(
      UserConsent.AI_ASSISTANCE, UserConsent.IDENTITY_VERIFICATION);

  private final UserRepository users;
  private final UserPreferenceRepository preferences;
  private final UserConsentRepository consents;
  private final AuditService auditService;

  public UserService(UserRepository users, UserPreferenceRepository preferences,
      UserConsentRepository consents, AuditService auditService) {
    this.users = users;
    this.preferences = preferences;
    this.consents = consents;
    this.auditService = auditService;
  }

  @Transactional(readOnly = true)
  public UserDto.Profile getProfile(UUID userId) {
    return UserDto.Profile.from(requireUser(userId));
  }

  @Transactional
  public UserDto.Profile updateProfile(UUID userId, String displayName, String phone) {
    User user = requireUser(userId);
    if (displayName != null) {
      user.setDisplayName(displayName.trim());
    }
    if (phone != null) {
      user.setPhone(phone.trim().isEmpty() ? null : phone.trim());
    }
    return UserDto.Profile.from(user);
  }

  @Transactional(readOnly = true)
  public UserDto.Preferences getPreferences(UUID userId) {
    requireUser(userId);
    return UserDto.Preferences.from(preferences.findById(userId)
        .orElseGet(() -> new UserPreference(userId)));
  }

  @Transactional
  public UserDto.Preferences updatePreferences(UUID userId, Boolean emailEnabled,
      Boolean smsEnabled, String locale) {
    requireUser(userId);
    UserPreference pref = preferences.findById(userId).orElseGet(() -> new UserPreference(userId));
    if (emailEnabled != null) {
      pref.setEmailEnabled(emailEnabled);
    }
    if (smsEnabled != null) {
      pref.setSmsEnabled(smsEnabled);
    }
    if (locale != null) {
      pref.setLocale(locale.trim());
    }
    return UserDto.Preferences.from(preferences.save(pref));
  }

  @Transactional
  public UserDto.Consent recordConsent(UUID userId, String purpose, boolean granted) {
    requireUser(userId);
    String normalized = purpose.trim().toUpperCase();
    if (!KNOWN_PURPOSES.contains(normalized)) {
      throw new UnprocessableException("Unknown consent purpose: " + purpose);
    }
    UserConsent consent = consents.save(new UserConsent(userId, normalized, granted));
    auditService.record(userId, null, AuditActions.CONSENT_RECORDED, "UserConsent",
        consent.getId().toString(), "SUCCESS", "purpose=" + normalized + ",granted=" + granted);
    return UserDto.Consent.from(consent);
  }

  @Transactional(readOnly = true)
  public Map<String, UserDto.Consent> currentConsents(UUID userId) {
    requireUser(userId);
    // Repository returns newest-first, so first occurrence per purpose wins.
    Map<String, UserDto.Consent> current = new LinkedHashMap<>();
    List<UserConsent> history = consents.findByUserIdOrderByRecordedAtDesc(userId);
    for (UserConsent consent : history) {
      current.putIfAbsent(consent.getPurpose(), UserDto.Consent.from(consent));
    }
    return current;
  }

  @Transactional(readOnly = true)
  public boolean hasGranted(UUID userId, String purpose) {
    return consents.findFirstByUserIdAndPurposeOrderByRecordedAtDesc(userId, purpose)
        .map(UserConsent::isGranted)
        .orElse(false);
  }

  private User requireUser(UUID userId) {
    return users.findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
  }
}
