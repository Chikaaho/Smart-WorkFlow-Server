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
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        user.setStatus(0);
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

    @Test
    @DisplayName("停用用户（status=1）登录 → 401 + 账号已停用，不签发任何 token")
    void login_withDisabledUser_shouldReturnFailure() {
        // -- Arrange --
        SysUser user = new SysUser();
        user.setId(1L);
        user.setTenantId(0L);
        user.setUsername("disabled");
        user.setPassword(passwordEncoder.encode("correct-password"));
        user.setStatus(1);
        when(sysUserService.getByUsername("disabled")).thenReturn(user);

        // -- Act --
        AuthController.LoginRequest request = new AuthController.LoginRequest();
        request.setUsername("disabled");
        request.setPassword("correct-password");
        R<TokenResponse> result = controller.login(request, mockResponse);

        // -- Assert --
        assertThat(result.getCode())
                .as("停用用户应返回 401")
                .isEqualTo(401);
        assertThat(result.getMsg())
                .as("提示应区分'账号已停用'")
                .isEqualTo("账号已停用");
        assertThat(result.getData())
                .as("失败时 data 应为 null")
                .isNull();
        assertThat(mockResponse.getCookie("rt"))
                .as("拒绝登录时不应下发 refresh cookie")
                .isNull();
    }

    @Test
    @DisplayName("锁定用户（status=2）登录 → 401 + 账号已锁定")
    void login_withLockedUser_shouldReturnFailure() {
        // -- Arrange --
        SysUser user = new SysUser();
        user.setId(1L);
        user.setTenantId(0L);
        user.setUsername("locked");
        user.setPassword(passwordEncoder.encode("correct-password"));
        user.setStatus(2);
        when(sysUserService.getByUsername("locked")).thenReturn(user);

        // -- Act --
        AuthController.LoginRequest request = new AuthController.LoginRequest();
        request.setUsername("locked");
        request.setPassword("correct-password");
        R<TokenResponse> result = controller.login(request, mockResponse);

        // -- Assert --
        assertThat(result.getCode())
                .as("锁定用户应返回 401")
                .isEqualTo(401);
        assertThat(result.getMsg())
                .as("提示应区分'账号已锁定'")
                .isEqualTo("账号已锁定");
        assertThat(result.getData())
                .as("失败时 data 应为 null")
                .isNull();
    }

    @Test
    @DisplayName("停用用户 refresh → 401 + 账号已停用 + 新轮换 token 已撤销 + cookie 已清除")
    void refresh_withDisabledUser_shouldRejectAndRevokeNewToken() {
        // -- Arrange --
        String newRawToken = "new-refresh-token-raw-64-chars-hex";
        when(refreshTokenService.rotateRefreshToken(anyString(), anyLong()))
                .thenReturn(new RefreshTokenService.RefreshTokenRotation(1L, 0L, newRawToken));
        SysUser user = new SysUser();
        user.setId(1L);
        user.setStatus(1);
        when(sysUserService.getById(1L)).thenReturn(user);

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setCookies(new Cookie("rt", "old-refresh-token-raw-64-chars-hex"));

        // -- Act --
        R<TokenResponse> result = controller.refresh(servletRequest, mockResponse);

        // -- Assert --
        assertThat(result.getCode())
                .as("停用用户 refresh 应返回 401")
                .isEqualTo(401);
        assertThat(result.getMsg())
                .as("提示应区分'账号已停用'")
                .isEqualTo("账号已停用");
        verify(refreshTokenService).revokeRefreshToken(newRawToken);
        assertThat(mockResponse.getCookie("rt"))
                .as("拒绝刷新时应下发清除 cookie")
                .isNotNull();
        assertThat(mockResponse.getCookie("rt").getValue())
                .as("清除 cookie 应为空值")
                .isEmpty();
        assertThat(mockResponse.getCookie("rt").getMaxAge())
                .as("清除 cookie 应 Max-Age=0")
                .isZero();
    }

    @Test
    @DisplayName("锁定用户 refresh → 401 + 账号已锁定 + 新轮换 token 已撤销")
    void refresh_withLockedUser_shouldRejectAndRevokeNewToken() {
        // -- Arrange --
        String newRawToken = "new-refresh-token-raw-64-chars-hex";
        when(refreshTokenService.rotateRefreshToken(anyString(), anyLong()))
                .thenReturn(new RefreshTokenService.RefreshTokenRotation(1L, 0L, newRawToken));
        SysUser user = new SysUser();
        user.setId(1L);
        user.setStatus(2);
        when(sysUserService.getById(1L)).thenReturn(user);

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setCookies(new Cookie("rt", "old-refresh-token-raw-64-chars-hex"));

        // -- Act --
        R<TokenResponse> result = controller.refresh(servletRequest, mockResponse);

        // -- Assert --
        assertThat(result.getCode())
                .as("锁定用户 refresh 应返回 401")
                .isEqualTo(401);
        assertThat(result.getMsg())
                .as("提示应区分'账号已锁定'")
                .isEqualTo("账号已锁定");
        verify(refreshTokenService).revokeRefreshToken(newRawToken);
    }

    @Test
    @DisplayName("refresh 用户已不存在（逻辑删除）→ 401 + 账号已停用 + 新轮换 token 已撤销")
    void refresh_withDeletedUser_shouldRejectAndRevokeNewToken() {
        // -- Arrange --
        String newRawToken = "new-refresh-token-raw-64-chars-hex";
        when(refreshTokenService.rotateRefreshToken(anyString(), anyLong()))
                .thenReturn(new RefreshTokenService.RefreshTokenRotation(1L, 0L, newRawToken));
        when(sysUserService.getById(1L)).thenReturn(null);

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setCookies(new Cookie("rt", "old-refresh-token-raw-64-chars-hex"));

        // -- Act --
        R<TokenResponse> result = controller.refresh(servletRequest, mockResponse);

        // -- Assert --
        assertThat(result.getCode())
                .as("用户不存在时 refresh 应返回 401")
                .isEqualTo(401);
        assertThat(result.getMsg())
                .as("用户不存在按'账号已停用'提示")
                .isEqualTo("账号已停用");
        verify(refreshTokenService).revokeRefreshToken(newRawToken);
    }

    @Test
    @DisplayName("正常用户 refresh → 新 access token + 新 refresh cookie，不撤销新 token")
    void refresh_withActiveUser_shouldReturnNewTokens() {
        // -- Arrange --
        String newRawToken = "new-refresh-token-raw-64-chars-hex";
        when(refreshTokenService.rotateRefreshToken(anyString(), anyLong()))
                .thenReturn(new RefreshTokenService.RefreshTokenRotation(1L, 0L, newRawToken));
        SysUser user = new SysUser();
        user.setId(1L);
        user.setStatus(0);
        when(sysUserService.getById(1L)).thenReturn(user);
        when(jwtTokenProvider.generateToken(1L)).thenReturn("test-jwt-token-refreshed");

        MockHttpServletRequest servletRequest = new MockHttpServletRequest();
        servletRequest.setCookies(new Cookie("rt", "old-refresh-token-raw-64-chars-hex"));

        // -- Act --
        R<TokenResponse> result = controller.refresh(servletRequest, mockResponse);

        // -- Assert --
        assertThat(result.getCode())
                .as("正常用户 refresh 应成功")
                .isZero();
        assertThat(result.getData().getAccessToken())
                .as("应返回新 access token")
                .isEqualTo("test-jwt-token-refreshed");
        assertThat(result.getData().getExpiresIn())
                .as("expiresIn 应按 JWT 配置返回")
                .isEqualTo(900);
        assertThat(mockResponse.getCookie("rt").getValue())
                .as("应下发新的 refresh cookie")
                .isEqualTo(newRawToken);
        verify(refreshTokenService, never())
                .revokeRefreshToken(anyString());
    }
}
