package com.academicvoting.auth;

import com.academicvoting.dto.ApiResponse;
import com.academicvoting.identity.EphemeralWalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * REST controller for Sprint 3 authentication and identity-blinding endpoints.
 *
 * <h2>Endpoints</h2>
 * <pre>
 *   POST  /api/auth/login               — university login → JWT
 *   POST  /api/auth/voting-credential   — issue ephemeral wallet for a poll (JWT required)
 *   GET   /api/auth/me                  — current user info (JWT required)
 * </pre>
 *
 * <h2>Typical Flow</h2>
 * <ol>
 *   <li>Student POSTs to {@code /login} → receives JWT token</li>
 *   <li>Student POSTs to {@code /voting-credential} with JWT → receives ephemeral private key</li>
 *   <li>Student POSTs to {@code /api/polls/{id}/vote} with ephemeral key (no JWT needed)</li>
 * </ol>
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final MockUserStore          userStore;
    private final JwtUtil                jwtUtil;
    private final EphemeralWalletService walletService;

    // ═════════════════════════════════════════════════════════════════════════
    // POST /api/auth/login
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Authenticates a university member and returns a signed JWT.
     *
     * <pre>
     * Request:
     * {
     *   "userId":   "student001",
     *   "password": "password123"
     * }
     *
     * Response 200:
     * {
     *   "token":     "eyJhbGci...",
     *   "tokenType": "Bearer",
     *   "userId":    "student001",
     *   "name":      "Alice Johnson",
     *   "role":      "STUDENT",
     *   "expiresAt": "2026-05-24T08:00:00Z"
     * }
     * </pre>
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {

        log.info("→ POST /api/auth/login — userId: {}", request.getUserId());

        // ── Look up user ──────────────────────────────────────────────────────
        UniversityUser user = userStore.findById(request.getUserId())
            .orElseThrow(() -> {
                log.warn("Login failed — unknown userId: {}", request.getUserId());
                // Return a generic error to prevent user-enumeration attacks
                return new IllegalArgumentException("Invalid credentials");
            });

        // ── Verify password ───────────────────────────────────────────────────
        if (!userStore.verifyPassword(user, request.getPassword())) {
            log.warn("Login failed — wrong password for userId: {}", request.getUserId());
            throw new IllegalArgumentException("Invalid credentials");
        }

        // ── Generate JWT ──────────────────────────────────────────────────────
        String token      = jwtUtil.generateToken(user);
        String expiresAt  = Instant.now().plusMillis(86_400_000).toString(); // 24h

        LoginResponse body = LoginResponse.builder()
            .token(token)
            .tokenType("Bearer")
            .userId(user.getUserId())
            .name(user.getName())
            .email(user.getEmail())
            .role(user.getRole())
            .expiresAt(expiresAt)
            .build();

        log.info("Login successful — userId: {} [{}]", user.getUserId(), user.getRole());
        return ResponseEntity.ok(ApiResponse.success(body));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // POST /api/auth/voting-credential
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Issues a one-time ephemeral voting credential for the authenticated voter.
     *
     * <p>Requires {@code Authorization: Bearer <token>} header.
     *
     * <p>The response contains the ephemeral private key — shown exactly once.
     * The server discards the key after this response.
     *
     * <pre>
     * Request:
     * { "pollId": 0 }
     *
     * Response 201:
     * {
     *   "pollId":             0,
     *   "ephemeralAddress":   "0xAbCd...",
     *   "ephemeralPrivateKey":"0x1234...",    ← save this! shown only once
     *   "commitment":         "sha256hex...",
     *   "whitelistTxHash":    "0xTxHash...",
     *   "warning":            "Save your ephemeralPrivateKey now..."
     * }
     * </pre>
     *
     * @param authentication Populated by {@link JwtFilter} from the Bearer token
     */
    @PostMapping("/voting-credential")
    public ResponseEntity<ApiResponse<CredentialResponse>> getVotingCredential(
            @Valid @RequestBody CredentialRequest request,
            Authentication authentication) {

        // authentication.getName() = userId from the JWT subject
        String userId = authentication.getName();
        long   pollId = request.getPollId();

        log.info("→ POST /api/auth/voting-credential — userId: [REDACTED], pollId: {}", pollId);

        // Double check role: admins are not permitted to vote/get credentials
        UniversityUser user = userStore.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));
        if (!"STUDENT".equals(user.getRole())) {
            throw new IllegalArgumentException("Only students are permitted to obtain voting credentials.");
        }

        CredentialResponse credential = walletService.issueCredential(userId, pollId);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.<CredentialResponse>builder()
                .status(201)
                .message("Voting credential issued. Save your ephemeralPrivateKey — " +
                         "it will NOT be shown again.")
                .data(credential)
                .build()
            );
    }

    // ═════════════════════════════════════════════════════════════════════════
    // GET /api/auth/me
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Returns the authenticated user's profile from the JWT claims.
     *
     * <p>Requires {@code Authorization: Bearer <token>} header.
     *
     * <pre>GET /api/auth/me</pre>
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<Map<String, Object>>> me(
            Authentication authentication,
            @RequestHeader("Authorization") String authHeader) {

        String token  = authHeader.substring("Bearer ".length()).trim();
        String userId = authentication.getName();
        String role   = jwtUtil.extractRole(token);
        String name   = jwtUtil.extractName(token);

        UniversityUser user = userStore.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        return ResponseEntity.ok(ApiResponse.success(Map.of(
            "userId",               userId,
            "name",                 name,
            "email",                user.getEmail(),
            "role",                 role,
            "credentialsIssued",    walletService.credentialCount(userId)
        )));
    }
}
