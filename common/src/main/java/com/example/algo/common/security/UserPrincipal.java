package com.example.algo.common.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPrincipal implements UserDetails {
    private String userId;
    private String username;
    private String email;
    private Set<String> roles;
    private Set<String> permissions;
    private boolean enabled;
    private boolean accountExpired;
    private boolean credentialsExpired;
    private boolean accountLocked;
    private String tenantId;
    private String sessionId;
    private Set<String> authorizedBrokers;

    @JsonIgnore
    public UserContext getUserContext() {
        return UserContext.builder()
                .userId(userId)
                .tenantId(tenantId)
                .sessionId(sessionId)
                .permissions(permissions)
                .brokerIds(
                        authorizedBrokers == null ? "" : String.join(",", authorizedBrokers)
                )
                .build();
    }

    @Override
    @JsonIgnore
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> authorities = roles == null ? Set.of() :
                roles.stream()
                        .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                        .collect(Collectors.toSet());

        if (permissions != null) {
            permissions.stream()
                    .map(SimpleGrantedAuthority::new)
                    .forEach(authorities::add);
        }

        return authorities;
    }

    @Override
    public String getPassword() {
        return null; // JWT, no passwords stored
    }

    @Override
    public String getUsername() {
        return username; // Required by UserDetails
    }

    @Override
    public boolean isAccountNonExpired() {
        return !accountExpired;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !accountLocked;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return !credentialsExpired;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
