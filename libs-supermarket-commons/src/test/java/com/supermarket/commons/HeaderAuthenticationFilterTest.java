package com.supermarket.commons;

import com.supermarket.commons.security.AuthenticatedUser;
import com.supermarket.commons.security.CurrentUserProvider;
import com.supermarket.commons.security.HeaderAuthenticationFilter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HeaderAuthenticationFilterTest {

    private final HeaderAuthenticationFilter filter = new HeaderAuthenticationFilter();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("identity headers forwarded by the gateway become an authenticated principal with its role")
    void identityHeaders_ShouldAuthenticate() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "3");
        request.addHeader("X-User-Email", "cashier@supermarket.com");
        request.addHeader("X-User-Name", "cashier1");
        request.addHeader("X-User-Role", "CASHIER");
        request.addHeader("X-User-Branch-Id", "1");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        AuthenticatedUser user = new CurrentUserProvider().getCurrentUser();
        assertThat(user).isEqualTo(new AuthenticatedUser(3L, "cashier@supermarket.com", "cashier1", "CASHIER", 1L));
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting(GrantedAuthority::getAuthority).containsExactly("ROLE_CASHIER");
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("without identity headers the request continues anonymous")
    void missingHeaders_ShouldStayAnonymous() throws Exception {
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(), chain);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
        assertThatThrownBy(() -> new CurrentUserProvider().getCurrentUser()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("a user without branch is authenticated with a null branch id")
    void missingBranch_ShouldBeNull() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "1");
        request.addHeader("X-User-Role", "ADMIN");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        AuthenticatedUser user = new CurrentUserProvider().getCurrentUser();
        assertThat(user.branchId()).isNull();
        assertThat(user.isAdmin()).isTrue();
    }
}
