package com.haagendazs.member.presentation.controller;

import com.haagendazs.common.response.ApiResponse;
import com.haagendazs.member.application.service.MemberService;
import com.haagendazs.member.presentation.dto.MemberDto;
import com.haagendazs.member.presentation.support.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/members")
@RequiredArgsConstructor
public class MemberController {

    private final MemberService memberService;

    @GetMapping("/me")
    public ApiResponse<MemberDto.MemberResponse> getMyProfile() {
        return ApiResponse.ok(MemberDto.MemberResponse.from(
                memberService.getMyProfile(SecurityUtils.getCurrentMemberId())
        ));
    }

    @PatchMapping("/me")
    public ApiResponse<MemberDto.MemberResponse> updateMyProfile(
            @Valid @RequestBody MemberDto.UpdateProfileRequest request
    ) {
        return ApiResponse.ok(MemberDto.MemberResponse.from(
                memberService.updateMyProfile(
                        SecurityUtils.getCurrentMemberId(),
                        request.nickname(),
                        request.profileImageUrl()
                )
        ));
    }

    @DeleteMapping("/me")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void withdraw() {
        memberService.withdraw(SecurityUtils.getCurrentMemberId());
    }

    @GetMapping("/{memberId}")
    public ApiResponse<MemberDto.MemberResponse> getMemberProfile(@PathVariable Long memberId) {
        return ApiResponse.ok(MemberDto.MemberResponse.from(
                memberService.getMemberProfile(memberId)
        ));
    }
}
