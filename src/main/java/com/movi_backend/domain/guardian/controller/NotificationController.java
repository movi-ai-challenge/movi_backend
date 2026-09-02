package com.movi_backend.domain.guardian.controller;

import com.movi_backend.domain.guardian.application.NotificationQueryService;
import com.movi_backend.domain.guardian.controller.docs.NotificationApiDocs;
import com.movi_backend.domain.guardian.dto.response.NotificationResponse;
import com.movi_backend.global.response.ApiResponse;
import com.movi_backend.global.response.PageResponse;
import com.movi_backend.global.security.AuthUser;
import com.movi_backend.global.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController implements NotificationApiDocs {

    private static final String EMPTY_LIST_VOICE_MESSAGE = "보호자에게 보낸 알림이 없어요.";

    private final NotificationQueryService notificationQueryService;

    @Override
    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> getNotifications(
            @CurrentUser final AuthUser authUser,
            @RequestParam(defaultValue = "0") final int page,
            @RequestParam(defaultValue = "20") final int size
    ) {
        final PageResponse<NotificationResponse> notifications =
                notificationQueryService.findMine(authUser.userId(), page, size);
        return ApiResponse.success(notifications, toVoiceMessage(notifications));
    }

    private String toVoiceMessage(final PageResponse<NotificationResponse> notifications) {
        if (notifications.content().isEmpty()) {
            return EMPTY_LIST_VOICE_MESSAGE;
        }
        return "보호자 알림이 %d건 있어요.".formatted(notifications.totalElements());
    }
}
