package com.haagendazs.member.presentation.controller;

import com.haagendazs.common.response.ApiResponse;
import com.haagendazs.member.application.dto.TokenResult;
import com.haagendazs.member.application.service.AuthService;
import com.haagendazs.member.presentation.dto.MemberDto;
import com.haagendazs.member.presentation.support.SecurityUtils;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<MemberDto.MemberResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.ok(MemberDto.MemberResponse.from(
                authService.signup(request.email(), request.password(), request.nickname())
        ));
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(TokenResponse.from(
                authService.login(request.email(), request.password())
        ));
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
        authService.logout(SecurityUtils.getCurrentMemberId());
    }

    @PostMapping("/reissue")
    public ApiResponse<TokenResponse> reissue(@Valid @RequestBody ReissueRequest request) {
        return ApiResponse.ok(TokenResponse.from(
                authService.reissue(request.refreshToken())
        ));
    }

    public record TokenResponse(
            String accessToken,
            String refreshToken
    ) {
        public static TokenResponse from(TokenResult result) {
            return new TokenResponse(result.accessToken(), result.refreshToken());
        }
    }

    public record SignupRequest(
            @NotBlank @Email String email,
            @NotBlank String password,
            @NotBlank String nickname
    ) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {
    }

    public record ReissueRequest(
            @NotBlank String refreshToken
    ) {
    }
}
