package com.sw.ck.system.controller;

import com.sw.ck.common.response.R;
import com.sw.ck.security.cache.LoginUserLoader;
import com.sw.ck.security.jwt.JwtProperties;
import com.sw.ck.security.jwt.JwtTokenProvider;
import com.sw.ck.security.spi.UserDetailsProvider;
import com.sw.ck.system.entity.SysUser;
import com.sw.ck.system.model.TokenResponse;
import com.sw.ck.system.service.RefreshTokenService;
import com.sw.ck.system.service.SysUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link AuthController} 单元测试。
 * <p>
 * 覆盖 login 的三条路径：
 * <ul>
 *   <li>Happy path：用户存在 + 密码正确 → 返回 TokenResponse，code===0</li>
 *   <li>用户不存在 → code!==0</li>
 *   <li>密码错误 → code!==0</li>
 * </ul>
 * 直接 Mock Service 层，无需装载 Spring 上下文。
 * </p>
 */
class AuthControllerTest {

    private final SysUserService sysUserService = mock(SysUserService.class);
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(10);
    private final JwtTokenProvider jwtTokenProvider = mock(JwtTokenProvider.class);
    private final UserDetailsProvider userDetailsProvider = mock(UserDetailsProvider.class);
    private final JwtProperties jwtProperties = mock(JwtProperties.class);
    private final RefreshTokenService refreshTokenService = mock(RefreshTokenService.class);
    private final LoginUserLoader loginUserLoader = mock(LoginUserLoader.class);
    private final MockHttpServletResponse mockResponse = new MockHttpServletResponse();
    private final AuthController controller = new AuthController(
            userDetailsProvider, passwordEncoder, jwtTokenProvider, sysUserService,
            jwtProperties, refreshTokenService, loginUserLoader);

    @BeforeEach
    void setUp() {
        // 默认 JWT 配置
        when(jwtProperties.getAccessExpireSeconds()).thenReturn(900L);
        when(jwtProperties.getRefreshExpireSeconds()).thenReturn(604800L);
        // 默认 refresh token 创建成功
        when(refreshTokenService.createRefreshToken(anyLong(), anyLong(), anyLong()))
                .thenReturn("test-refresh-token-raw-64-chars-hex");
    }

    @Test
    @DisplayName("Happy path：用户存在 + 密码正确 → 返回 TokenResponse，code===0")
    void login_withValidCredentials_shouldReturnToken() {
        // -- Arrange --
        SysUser user = new SysUser();
        user.setId(1L);
        user.setTenantId(0L);
        user.setUsername("admin");
        user.setPassword(passwordEncoder.encode("admin123"));
        when(sysUserService.getByUsername("admin")).thenReturn(user);
        when(jwtTokenProvider.generateToken(1L)).thenReturn("test-jwt-token");

        // -- Act --
        AuthController.LoginRequest request = new AuthController.LoginRequest();
        request.setUsername("admin");
        request.setPassword("admin123");
        R<TokenResponse> result = controller.login(request, mockResponse);

        // -- Assert --
        assertThat(result.getCode())
                .as("成功码应为 0")
                .isZero();
        assertThat(result.getData())
                .as("TokenResponse 不应为 null")
                .isNotNull();
        assertThat(result.getData().getAccessToken())
                .as("应返回有效 access token")
                .isNotBlank()
                .isEqualTo("test-jwt-token");
        assertThat(result.getData().getExpiresIn())
                .as("expiresIn 应按 JWT 配置返回")
                .isEqualTo(900);
        // 验证 refresh cookie 已设置
        assertThat(mockResponse.getCookie("rt"))
                .as("应设置名为 'rt' 的 refresh cookie")
                .isNotNull();
        assertThat(mockResponse.getCookie("rt").getValue())
                .as("refresh cookie 值应为 raw token")
                .isEqualTo("test-refresh-token-raw-64-chars-hex");
    }

    @Test
    @DisplayName("用户不存在 → code!==0")
    void login_withUnknownUser_shouldReturnFailure() {
        // -- Arrange --
        when(sysUserService.getByUsername("unknown")).thenReturn(null);

        // -- Act --
        AuthController.LoginRequest request = new AuthController.LoginRequest();
        request.setUsername("unknown");
        request.setPassword("any-password");
        R<TokenResponse> result = controller.login(request, mockResponse);

        // -- Assert --
        assertThat(result.getCode())
                .as("用户不存在时应返回非 0 错误码")
                .isNotZero();
        assertThat(result.getData())
                .as("失败时 data 应为 null")
                .isNull();
    }

    @Test
    @DisplayName("密码错误 → code!==0")
    void login_withWrongPassword_shouldReturnFailure() {
        // -- Arrange --
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword(passwordEncoder.encode("correct-password"));
        when(sysUserService.getByUsername("admin")).thenReturn(user);

        // -- Act --
        AuthController.LoginRequest request = new AuthController.LoginRequest();
        request.setUsername("admin");
        request.setPassword("wrong-password");
        R<TokenResponse> result = controller.login(request, mockResponse);

        // -- Assert --
        assertThat(result.getCode())
                .as("密码错误时应返回非 0 错误码")
                .isNotZero();
        assertThat(result.getMsg())
                .as("失败消息应包含提示")
                .isNotNull();
    }
}
