package com.movi_backend.global.security;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 현재 인증된 사용자를 컨트롤러 파라미터로 주입한다.
 *
 * <pre>
 * &#64;GetMapping("/accounts")
 * public ApiResponse&lt;List&lt;AccountResponse&gt;&gt; getAccounts(&#64;CurrentUser AuthUser authUser) {
 *     return ApiResponse.success(accountService.findAll(authUser.userId()));
 * }
 * </pre>
 *
 * <p>인증 정보가 없으면 {@code AUTH_4010}으로 거부된다.
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface CurrentUser {
}
