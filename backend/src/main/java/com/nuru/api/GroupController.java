package com.nuru.api;

import com.nuru.api.dto.GroupDto;
import com.nuru.auth.JwtAuthenticationFilter;
import com.nuru.service.GroupService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/groups")
@Tag(name = "Groups")
public class GroupController {

  private final GroupService groupService;

  public GroupController(GroupService groupService) {
    this.groupService = groupService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public GroupDto.Response create(@Valid @RequestBody GroupDto.CreateRequest request) {
    return groupService.createGroup(currentUser(), request.name(), request.description(),
        request.currency());
  }

  @GetMapping
  public Page<GroupDto.Response> listMine(@PageableDefault(size = 20) Pageable pageable) {
    return groupService.listMyGroups(currentUser(), pageable);
  }

  @GetMapping("/{groupId}")
  public GroupDto.Response get(@PathVariable UUID groupId) {
    return groupService.getGroup(groupId, currentUser());
  }

  @PatchMapping("/{groupId}")
  public GroupDto.Response update(@PathVariable UUID groupId,
      @Valid @RequestBody GroupDto.UpdateRequest request) {
    return groupService.updateGroup(groupId, currentUser(), request.name(),
        request.description());
  }

  @GetMapping("/{groupId}/roles")
  public List<GroupDto.Role> listRoles(@PathVariable UUID groupId) {
    return groupService.listRoles(groupId, currentUser());
  }

  @PostMapping("/{groupId}/roles")
  @ResponseStatus(HttpStatus.CREATED)
  public GroupDto.Role createRole(@PathVariable UUID groupId,
      @Valid @RequestBody GroupDto.CreateRoleRequest request) {
    return groupService.createCustomRole(groupId, currentUser(), request.name(),
        request.permissions());
  }

  @PatchMapping("/{groupId}/roles/{roleId}")
  public GroupDto.Role updateRole(@PathVariable UUID groupId, @PathVariable UUID roleId,
      @Valid @RequestBody GroupDto.UpdateRoleRequest request) {
    return groupService.updateRolePermissions(groupId, currentUser(), roleId,
        request.permissions());
  }

  @DeleteMapping("/{groupId}/roles/{roleId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteRole(@PathVariable UUID groupId, @PathVariable UUID roleId) {
    groupService.deleteRole(groupId, currentUser(), roleId);
  }

  private UUID currentUser() {
    return JwtAuthenticationFilter.principal();
  }
}
